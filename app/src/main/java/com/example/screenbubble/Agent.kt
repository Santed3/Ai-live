package com.example.screenbubble

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tool-calling assistant that can read the screen and control the phone through ReaderService. */
class Agent(
    private val ctx: Context,
    private val client: OpenAiClient,
    private val svc: ReaderService,
    private val ui: Ui
) {
    interface Ui {
        fun status(t: String)
        fun confirm(t: String): Boolean
    }

    private fun systemPrompt(): String {
        val now = SimpleDateFormat("EEEE d MMMM yyyy HH:mm", Locale("sv", "SE")).format(Date())
        val mem = Store.memory(ctx)
        return "Du är ScreenBubble, en snabb och vänlig röst- och textassistent på användarens telefon, ungefär som Siri fast mycket smartare. " +
            "Du ser användarens skärm (bild + text) och kan styra telefonen med verktyg.\n" +
            "Regler:\n" +
            "- Svara kort, naturligt och på användarens språk. Använd inte markdown.\n" +
            "- Använd verktyg bara när användaren vill att något ska göras eller du behöver mer information. Annars svara direkt.\n" +
            "- Efter varje handling får du ny skärmtext. Kontrollera att det blev rätt innan du går vidare. Max 10 steg.\n" +
            "- Sätt consequential=true när en handling skickar, raderar, betalar, publicerar eller ändrar något som är svårt att ångra. Användaren får då bekräfta.\n" +
            "- compose_sms och compose_email öppnar bara ett utkast. Användaren trycker själv på skicka.\n" +
            "- Fråga användaren om något viktigt saknas, till exempel mottagare eller tidpunkt.\n" +
            "- Gör aldrig något användaren inte bett om. Nekar användaren en bekräftelse: sluta och säg det kort.\n" +
            "- Att googla: använd open_url med https://www.google.com/search?q=... så användaren ser sökningen på skärmen. Använd web_search (i bakgrunden) bara när användaren vill ha ett sammanfattat svar eller research.\n" +
            "- Gör saker synligt på skärmen (öppna appar, skriv i fält) i stället för i bakgrunden.\n" +
            "- Använd remember när användaren ber dig komma ihåg något.\n" +
            "Nu: $now\n" +
            if (mem.isEmpty()) "" else "Saker användaren bett dig komma ihåg:\n" + mem.joinToString("\n") { "- $it" }
    }

    /** Instant local handling of simple commands, no API round trip. */
    private fun fastCommand(t: String): String? {
        val s = t.trim()
        if (Regex("sammanfatta|jämför|förklara|berätta|research|och sedan|och sen", RegexOption.IGNORE_CASE).containsMatchIn(s)) return null
        Regex("^(?:google|googla|sök(?: efter| på)?(?: google)?)\\s+(.+)$", RegexOption.IGNORE_CASE).find(s)?.let {
            val q = it.groupValues[1].trim()
            ui.status("Googlar…")
            svc.openUrl("https://www.google.com/search?q=" + java.net.URLEncoder.encode(q, "UTF-8"))
            return "Googlar: $q"
        }
        Regex("^(?:öppna|starta)\\s+(.+)$", RegexOption.IGNORE_CASE).find(s)?.let {
            val n = it.groupValues[1].trim()
            ui.status("Öppnar $n…")
            return svc.openApp(n)
        }
        return null
    }

    fun run(userText: String, screenText: String, shot: String?): String {
        fastCommand(userText)?.let { return it }
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt()))
        val h = Store.history(ctx)
        for (i in 0 until h.length()) messages.put(h.get(i))
        val first = "Användarens meddelande: $userText\n\nAktiv app: ${svc.currentPkg()}\nText på skärmen:\n${screenText.take(6000)}"
        messages.put(JSONObject().put("role", "user").put("content", client.content(first, shot)))

        var answer = ""
        for (step in 0 until 10) {
            val msg = client.chat(messages, TOOLS, 900)
            val calls = msg.optJSONArray("tool_calls")
            if (calls == null || calls.length() == 0) {
                answer = if (msg.isNull("content")) "" else msg.getString("content").trim()
                break
            }
            val assistant = JSONObject().put("role", "assistant")
                .put("content", if (msg.isNull("content")) "" else msg.getString("content"))
                .put("tool_calls", calls)
            messages.put(assistant)
            for (i in 0 until calls.length()) {
                val c = calls.getJSONObject(i)
                val fn = c.getJSONObject("function")
                val name = fn.getString("name")
                val args = try {
                    JSONObject(fn.optString("arguments", "{}"))
                } catch (e: Exception) {
                    JSONObject()
                }
                val result = try {
                    exec(name, args)
                } catch (e: Exception) {
                    "Fel: ${e.message}"
                }
                messages.put(
                    JSONObject().put("role", "tool").put("tool_call_id", c.getString("id")).put("content", result)
                )
            }
        }
        if (answer.isEmpty()) answer = "Jag hann inte klart. Försök igen eller dela upp uppgiften."
        Store.addHistory(ctx, "user", userText)
        Store.addHistory(ctx, "assistant", answer)
        return answer
    }

    private val sensitive = Regex(
        "(skicka|send|radera|delete|ta bort|remove|betala|pay|köp|buy|beställ|order|posta|post|publicera|logga ut|log out|bekräfta|confirm|submit|godkänn)",
        RegexOption.IGNORE_CASE
    )

    private fun after(prefix: String): String {
        Thread.sleep(900)
        return prefix + "\nNy skärm (app: ${svc.currentPkg()}):\n" + svc.readScreenText().take(2500)
    }

    private fun exec(name: String, a: JSONObject): String {
        when (name) {
            "read_screen" -> {
                ui.status("Läser skärmen…")
                return "App: ${svc.currentPkg()}\n" + svc.readScreenText().take(5000)
            }
            "open_app" -> {
                val n = a.optString("name")
                ui.status("Öppnar $n…")
                return after(svc.openApp(n))
            }
            "open_url" -> {
                val u = a.optString("url")
                ui.status("Öppnar $u…")
                return after(svc.openUrl(u))
            }
            "tap_text" -> {
                val t = a.optString("text")
                if ((a.optBoolean("consequential") || sensitive.containsMatchIn(t)) &&
                    !ui.confirm("Tryck på \"$t\"?")
                ) return "Användaren nekade åtgärden."
                ui.status("Trycker på \"$t\"…")
                return after(svc.tapText(t))
            }
            "tap_position" -> {
                if (a.optBoolean("consequential") && !ui.confirm("Genomföra den här åtgärden på skärmen?")) {
                    return "Användaren nekade åtgärden."
                }
                ui.status("Trycker på skärmen…")
                val ok = svc.tapFraction(a.optDouble("x").toFloat(), a.optDouble("y").toFloat())
                return after(if (ok) "Tryckte." else "Kunde inte trycka.")
            }
            "type_text" -> {
                ui.status("Skriver…")
                return after(svc.typeText(a.optString("text"), a.optBoolean("append")))
            }
            "press_enter" -> {
                if (a.optBoolean("consequential") && !ui.confirm("Trycka Enter (kan skicka/skicka in)?")) {
                    return "Användaren nekade åtgärden."
                }
                return after(svc.pressEnter())
            }
            "scroll" -> {
                ui.status("Scrollar…")
                return after(svc.scroll(a.optString("direction", "down")))
            }
            "global_action" -> {
                val act = a.optString("action")
                ui.status("Åtgärd: $act")
                return after(svc.globalAct(act))
            }
            "compose_sms" -> {
                ui.status("Förbereder SMS…")
                return after(svc.composeSms(a.optString("number"), a.optString("message")))
            }
            "compose_email" -> {
                ui.status("Förbereder mejl…")
                return after(svc.composeEmail(a.optString("to"), a.optString("subject"), a.optString("body")))
            }
            "dial" -> return after(svc.dial(a.optString("number")))
            "set_alarm" -> return svc.setAlarm(a.optInt("hour"), a.optInt("minute"), a.optString("label"))
            "set_timer" -> return svc.setTimer(a.optInt("seconds"), a.optString("label"))
            "web_search" -> {
                val q = a.optString("query")
                ui.status("Söker: $q")
                return client.search(q)
            }
            "remember" -> {
                Store.addMemory(ctx, a.optString("fact"))
                return "Sparat."
            }
        }
        return "Okänt verktyg: $name"
    }

    companion object {
        private fun tool(
            name: String,
            desc: String,
            props: List<Triple<String, String, String>> = emptyList(),
            required: List<String> = emptyList()
        ): JSONObject {
            val p = JSONObject()
            for ((k, type, d) in props) p.put(k, JSONObject().put("type", type).put("description", d))
            return JSONObject().put("type", "function").put(
                "function", JSONObject().put("name", name).put("description", desc).put(
                    "parameters", JSONObject().put("type", "object").put("properties", p)
                        .put("required", JSONArray(required))
                )
            )
        }

        private val conseq = Triple("consequential", "boolean", "true om handlingen skickar, raderar, betalar, publicerar eller är svår att ångra")

        val TOOLS: JSONArray = JSONArray()
            .put(tool("read_screen", "Läs av aktuell skärm som text."))
            .put(tool("open_app", "Öppna en app via namn.", listOf(Triple("name", "string", "Appens namn, t.ex. Chrome, Gmail")), listOf("name")))
            .put(tool("open_url", "Öppna en webbadress i webbläsaren.", listOf(Triple("url", "string", "Hela adressen")), listOf("url")))
            .put(tool("tap_text", "Tryck på ett element på skärmen som har viss text eller beskrivning.", listOf(Triple("text", "string", "Texten på knappen/elementet"), conseq), listOf("text")))
            .put(tool("tap_position", "Tryck på en position på skärmen. x och y är bråkdelar 0..1 av skärmbredd/-höjd i skärmbilden.", listOf(Triple("x", "number", "0..1 från vänster"), Triple("y", "number", "0..1 uppifrån"), conseq), listOf("x", "y")))
            .put(tool("type_text", "Skriv text i det aktiva textfältet (eller första textfältet).", listOf(Triple("text", "string", "Texten"), Triple("append", "boolean", "true för att lägga till efter befintlig text")), listOf("text")))
            .put(tool("press_enter", "Tryck Enter/skicka-åtgärd i textfältet.", listOf(conseq)))
            .put(tool("scroll", "Scrolla aktuell vy.", listOf(Triple("direction", "string", "up eller down")), listOf("direction")))
            .put(tool("global_action", "Systemåtgärd: back, home, recents, notifications, quick_settings.", listOf(Triple("action", "string", "back | home | recents | notifications | quick_settings")), listOf("action")))
            .put(tool("compose_sms", "Öppna ett SMS-utkast. Användaren trycker själv på skicka.", listOf(Triple("number", "string", "Telefonnummer"), Triple("message", "string", "Meddelandetext")), listOf("number", "message")))
            .put(tool("compose_email", "Öppna ett mejlutkast. Användaren trycker själv på skicka.", listOf(Triple("to", "string", "Mottagare"), Triple("subject", "string", "Ämne"), Triple("body", "string", "Text")), listOf("subject", "body")))
            .put(tool("dial", "Öppna uppringaren med ett nummer (ringer inte själv).", listOf(Triple("number", "string", "Telefonnummer")), listOf("number")))
            .put(tool("set_alarm", "Ställ ett alarm.", listOf(Triple("hour", "integer", "Timme 0-23"), Triple("minute", "integer", "Minut 0-59"), Triple("label", "string", "Namn")), listOf("hour", "minute")))
            .put(tool("set_timer", "Starta en timer.", listOf(Triple("seconds", "integer", "Sekunder"), Triple("label", "string", "Namn")), listOf("seconds")))
            .put(tool("web_search", "Sök på webben och få en sammanfattning med källor.", listOf(Triple("query", "string", "Sökfråga")), listOf("query")))
            .put(tool("remember", "Spara ett faktum om användaren till senare.", listOf(Triple("fact", "string", "Faktat att komma ihåg")), listOf("fact")))
    }
}
