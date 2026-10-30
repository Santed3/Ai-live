package com.example.screenbubble

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Start screen: setup checklist, updates, custom commands, memory. */
class MainActivity : Activity() {

    private val BG = 0xFF0B0E16.toInt()
    private val CARD = 0xFF171D2B.toInt()
    private val TEXT = 0xFFF2F4FC.toInt()
    private val MUTED = 0xFF9AA5BE.toInt()
    private val ACCENT = 0xFF8DF5D0.toInt()
    private val PRIMARY = 0xFF7857FF.toInt()

    private lateinit var content: LinearLayout
    private var keyInput: EditText? = null
    private var newerBuild: Long? = null
    private var updateText: String? = null
    private var updating = false
    private var activeTab = "home"

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, r: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(color, Color.rgb((Color.red(color) * 0.82f).toInt(), (Color.green(color) * 0.86f).toInt(), (Color.blue(color) * 0.92f).toInt()))).apply {
        cornerRadius = dp(r).toFloat()
        setStroke(dp(1), 0x1FFFFFFF)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(34), dp(18), dp(32))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(content) })
        render()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        if (i.getBooleanExtra("newcmd", false)) { i.removeExtra("newcmd"); commandDialog(null) }
        if (i.getBooleanExtra("checkupdate", false)) { i.removeExtra("checkupdate"); checkUpdate(true) }
    }

    override fun onResume() {
        super.onResume()
        render()
        checkUpdate(false)
    }

    override fun onPause() {
        saveKey()
        super.onPause()
    }

    // ------------------------------------------------------------------ helpers

    private fun saveKey() {
        val et = keyInput ?: return
        getSharedPreferences("prefs", MODE_PRIVATE).edit().putString("api_key", et.text.toString().trim()).apply()
    }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = t
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun card(title: String?, sub: String? = null): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(CARD, 18)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        if (title != null) c.addView(text(title, 16f, TEXT, true))
        if (sub != null) c.addView(text(sub, 13f, MUTED).apply { setPadding(0, dp(2), 0, dp(4)) })
        if (shouldShowCard(title)) content.addView(c, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        return c
    }

    private fun button(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        this.text = label
        isAllCaps = false
        textSize = 13.5f
        setTextColor(if (primary) Color.WHITE else TEXT)
        background = if (primary) GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF8A6BFF.toInt(), 0xFF6241E8.toInt())).apply { cornerRadius = dp(14).toFloat(); setStroke(dp(1), 0x447FFFFF) } else rounded(0x1FFFFFFF, 14)
        stateListAnimator = null
        minHeight = dp(40)
        minimumHeight = dp(40)
        setPadding(dp(14), 0, dp(14), 0)
        setOnClickListener { onClick() }
    }

    private fun small(label: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = label
        textSize = 14f
        setTextColor(MUTED)
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(6), dp(10), dp(6))
        setOnClickListener { onClick() }
    }

    private fun step(parent: LinearLayout, done: Boolean?, title: String, sub: String, action: String, onClick: () -> Unit) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(4)) }
        row.addView(TextView(this).apply {
            this.text = when (done) { true -> "✓"; false -> "○"; null -> "•" }
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(if (done == true) ACCENT else MUTED)
        }, LinearLayout.LayoutParams(dp(28), -2))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(title, 14.5f, TEXT, true))
        col.addView(text(sub, 12.5f, MUTED))
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        if (done != true) row.addView(button(action, false, onClick), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        parent.addView(row)
    }

    // ------------------------------------------------------------------ screen

    private fun render() {
        content.removeAllViews()
        keyInput = null
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
        val on = ReaderService.instance != null
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("onboarding_complete", false)) { renderOnboarding(prefs); return }

        // header
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFB9A8FF.toInt(), 0xFF6C47FF.toInt())).apply { shape = GradientDrawable.OVAL }
        }, LinearLayout.LayoutParams(dp(14), dp(14)).apply { rightMargin = dp(10) })
        head.addView(text("ScreenBubble", 28f, TEXT, true))
        content.addView(head)
        content.addView(text("Din AI-assistent · alltid nära till hands · v$ver", 13f, MUTED).apply { setPadding(0, dp(5), 0, dp(5)) })
        addTabs()
        content.addView(text("SIDEBAR · AI-ASSISTENT", 10f, ACCENT, true).apply {
            letterSpacing = 0.14f
            setPadding(dp(1), dp(12), 0, dp(1))
        })

        // update
        val nb = newerBuild
        if (nb != null || updateText != null) {
            val c = card(if (nb != null) "Ny version finns" else "Uppdatering")
            c.setBackgroundColor(Color.TRANSPARENT)
            c.background = rounded(0xFF12302A.toInt(), 18)
            c.addView(text(updateText ?: "Version 1.0.${nb!! - 100} är redo. Din data och inställningar behålls.", 13f, 0xFFCFEEE2.toInt()).apply { setPadding(0, dp(2), 0, dp(8)) })
            if (!updating) c.addView(button("Uppdatera nu", true) { startUpdate() })
        }

        // setup
        val setup = card("Kom igång", if (on) "Allt viktigt är på." else "Tre steg tar dig hela vägen.")
        step(setup, null, "Tillåt begränsade inställningar", "Android 13+: ⋮ i appinfo, välj \"Tillåt begränsade inställningar\".", "Appinfo") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        step(setup, on, "Slå på tjänsten", "Tillgänglighet → ScreenBubble → På.", "Öppna") {
            saveKey(); startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        step(setup, mic, "Tillåt mikrofon", "För röststyrning och samtal.", "Tillåt") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        step(setup, Updater.canInstall(this), "Tillåt uppdateringar", "Så kan appen uppdatera sig själv.", "Tillåt") {
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
        }

        if (Config.embeddedKey().isEmpty()) {
            val k = card("OpenAI API-nyckel", "Krävs för att assistenten ska svara.")
            val et = EditText(this).apply {
                hint = "sk-..."
                setHintTextColor(MUTED)
                setTextColor(TEXT)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                background = rounded(0x1FFFFFFF, 12)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setText(prefs.getString("api_key", ""))
            }
            keyInput = et
            k.addView(et)
        }

        // actions
        val act = card("Använd", "Tryck på sidopanelen eller den flytande symbolen för att öppna chatten. Tryck på … för fler funktioner.")
        val row = LinearLayout(this)
        row.addView(button("Öppna menyn", true) { saveKey(); ReaderService.instance?.setMode(ReaderService.Mode.MENU) ?: toast("Slå på tjänsten först") }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(6) })
        val hidden = ReaderService.instance?.isHidden() == true
        if (hidden) row.addView(button("Tillbaka till sidebar") { ReaderService.instance?.setMode(ReaderService.Mode.BAR) ; render() }, LinearLayout.LayoutParams(0, -2, 1f))
        else row.addView(button("Hide-läge") { saveKey(); ReaderService.instance?.setMode(ReaderService.Mode.HIDE) ?: toast("Slå på tjänsten först"); render() }, LinearLayout.LayoutParams(0, -2, 1f))
        act.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        act.addView(text("Markera text i valfri app och välj \"Fråga ScreenBubble\" för att få den förklarad.", 12.5f, MUTED).apply { setPadding(0, dp(10), 0, 0) })

        val appearance = card("Assistentens utseende", "Välj hur du vill öppna ScreenBubble ovanpå andra appar.")
        val display = Store.assistantDisplay(this)
        val appearanceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        for ((value, label) in listOf("sidebar" to "Sidopanel", "bubble" to "Flytande", "swipe" to "Svep")) {
            appearanceRow.addView(button(label, display == value) {
                Store.setAssistantDisplay(this, value)
                ReaderService.instance?.setMode(ReaderService.Mode.BAR)
                render()
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(5) })
        }
        appearance.addView(appearanceRow)
        val swipeLabel = if (Store.assistantSwipe(this) == "left") "vänster" else "höger"
        appearance.addView(text("Svep åt $swipeLabel för att öppna menyn. Svep åt andra hållet för att öppna chatten.", 12f, MUTED).apply { setPadding(0, dp(8), 0, dp(4)) })
        appearance.addView(button("Byt svepriktning", false) {
            Store.setAssistantSwipe(this, if (Store.assistantSwipe(this) == "left") "right" else "left")
            render()
        })

        // smart replies
        val sr = card("Smarta svar", "När någon skriver till dig i en chatt får du förslag på svar direkt i sidebaren, skrivna som du skriver.")
        fun toggle(label: String, key: String) {
            sr.addView(Switch(this).apply {
                text = label
                textSize = 14f
                setTextColor(TEXT)
                isChecked = Store.flag(this@MainActivity, key)
                setPadding(0, dp(8), 0, dp(4))
                setOnCheckedChangeListener { _, v -> Store.setFlag(this@MainActivity, key, v) }
            })
        }
        toggle("Active Help – proaktiv hjälp", "active_help")
        sr.addView(text("ScreenBubble granskar synlig skärmtext lokalt efter vanliga fel och fastnade steg. Inget skickas till AI förrän du trycker på förslaget. Den frågar alltid innan den tar över.", 12f, MUTED).apply { setPadding(0, dp(0), 0, dp(6)) })
        toggle("Föreslå svar och förbättra utkast", "smart_suggest")
        toggle("Lär dig hur jag skriver", "learn_style")
        sr.addView(text("Välj appar där Smart Reply får läsa sammanhang och föreslå svar eller förbättra ett utkast:", 12f, MUTED).apply { setPadding(0, dp(8), 0, dp(4)) })
        for ((pkg, appName) in Suggest.communicationApps) {
            sr.addView(Switch(this).apply {
                text = appName
                textSize = 13f
                setTextColor(TEXT)
                isChecked = Store.smartReplyAppEnabled(this@MainActivity, pkg)
                setPadding(dp(2), dp(4), 0, dp(4))
                setOnCheckedChangeListener { _, enabled -> Store.setSmartReplyAppEnabled(this@MainActivity, pkg, enabled) }
            })
        }
        val n = Store.chatSamples(this).size
        sr.addView(text(
            "Smart Reply läser konversationen och ditt pågående utkast i de appar du valt. Texten skickas till OpenAI när förslag skapas, bara medan sidebaren är synlig. Tryck på ett förslag för att fylla i textfältet – det skickas aldrig automatiskt. " +
                "Lärande: skickade meddelanden sparas lokalt och relevanta stilprov skickas med AI-anropen. Lösenordsfält och känsligt innehåll filtreras bort så långt det kan identifieras. " +
                if (n > 0) "Hittills inlärda meddelanden: $n." else "Inget inlärt än.",
            12f, MUTED
        ).apply { setPadding(0, dp(6), 0, dp(4)) })
        if (n > 0 || Store.styleProfile(this).isNotEmpty()) {
            sr.addView(button("Rensa det jag lärt mig om min stil") { Store.clearWritingStyle(this); render() }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
        }

        // App profiles are configured here; the sidebar adapts to the active app.
        val profiles = card("Appprofiler", "Välj hur ScreenBubble ska anpassa sig automatiskt efter appen du använder.")
        fun profileToggle(label: String, key: String, detail: String) {
            profiles.addView(Switch(this).apply {
                text = label
                textSize = 14f
                setTextColor(TEXT)
                isChecked = Store.flag(this@MainActivity, key)
                setPadding(0, dp(7), 0, 0)
                setOnCheckedChangeListener { _, v -> Store.setFlag(this@MainActivity, key, v) }
            })
            profiles.addView(text(detail, 11.5f, MUTED).apply { setPadding(0, 0, 0, dp(5)) })
        }
        profileToggle("E-post och meddelanden", "profile_communication", "Prioriterar sammanfattningar och naturliga svar i din skrivstil.")
        profileToggle("Studier och lärande", "profile_study", "Förklarar stegvis och ger ledtrådar innan hela lösningen.")
        profileToggle("Skrivande och dokument", "profile_writing", "Hjälper med formulering, struktur, korrektur och dokument.")
        profileToggle("Webb och research", "profile_research", "Prioriterar källor, jämförelser och tydliga sammanfattningar.")

        // custom commands
        val cmds = Store.commands(this)
        val cc = card("Egna snabbkommandon", "Knappar i menyn som kör din egen instruktion, t.ex. \"Svara artigt på svenska\".")
        for (c in cmds) {
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
            r.addView(text(c.emoji, 20f, TEXT), LinearLayout.LayoutParams(dp(32), -2))
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(text(c.name, 14.5f, TEXT, true))
            col.addView(text(c.prompt.take(70), 12f, MUTED).apply { maxLines = 1 })
            r.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(small("✎") { commandDialog(c) })
            r.addView(small("✕") { Store.deleteCommand(this, c.id); render() })
            cc.addView(r)
        }
        cc.addView(button("＋ Nytt kommando") { commandDialog(null) }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })

        // memory
        val mem = Store.memory(this)
        val mc = card("Minne", if (mem.isEmpty()) "Du bestämmer vad ScreenBubble ska komma ihåg. Du kan granska och radera sparade uppgifter här." else "Det här har jag lärt mig om dig:")
        mc.addView(Switch(this).apply {
            text = "Lär av våra samtal automatiskt"
            textSize = 14f
            setTextColor(TEXT)
            isChecked = Store.flag(this@MainActivity, "memory_auto")
            setPadding(0, dp(7), 0, dp(7))
            setOnCheckedChangeListener { _, v -> Store.setFlag(this@MainActivity, "memory_auto", v) }
        })
        mc.addView(text("När detta är på kan ScreenBubble spara användbara, långsiktiga fakta. Känsliga uppgifter filtreras bort. Du kan alltid säga ”kom ihåg …” eller ta bort sparade minnen.", 11.5f, MUTED))
        mem.forEachIndexed { i, m ->
            val r = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            r.addView(text(m, 13f, TEXT), LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(small("✕") { Store.setMemory(this, mem.filterIndexed { j, _ -> j != i }); render() })
            mc.addView(r)
        }
        if (mem.isNotEmpty()) mc.addView(button("Glöm allt") { Store.setMemory(this, emptyList()); render() }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })

        // status + help
        val err = prefs.getString("last_error", "").orEmpty()
        if (err.isNotEmpty()) card("Senaste fel", err)
        val help = card("Så funkar det")
        help.addView(text(
            "Assistenten: tryck på sidopanelen eller symbolen för att öppna chatten. Tryck på … för fler funktioner. I svepläget öppnar vald svepriktning menyn och motsatt riktning chatten.\n\n" +
                "Hide-läge: bara en liten svart prick. Ett tryck ger ett kort svar, håll in för menyn, dubbeltryck stänger av.\n\n" +
                "Skicka och andra känsliga åtgärder kräver alltid att du bekräftar.\n\n" +
                "Skärmens innehåll skickas till OpenAI när du ställer en fråga. Använd inte appen där hjälpmedel är förbjudna, till exempel på prov.",
            13f, MUTED
        ))
    }

    private fun shouldShowCard(title: String?): Boolean = when (activeTab) {
        "home" -> title in setOf("Ny version finns", "Uppdatering", "Kom igång", "Använd", "Senaste fel", "Så funkar det")
        "assistant" -> title in setOf("Assistentens utseende", "Smarta svar", "Appprofiler")
        else -> title in setOf("Egna snabbkommandon", "Minne")
    }

    private fun addTabs() {
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, dp(3)) }
        for ((id, label) in listOf("home" to "Hem", "assistant" to "Assistent", "personal" to "Personligt")) {
            nav.addView(button(label, activeTab == id) { activeTab = id; render() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(6) })
        }
        content.addView(nav, LinearLayout.LayoutParams(-1, -2))
    }

    private fun renderOnboarding(prefs: android.content.SharedPreferences) {
        content.removeAllViews()
        keyInput = null
        val logoRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(14), 0, dp(12)) }
        logoRow.addView(text("S", 25f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFB9A8FF.toInt(), 0xFF6C47FF.toInt())).apply { shape = GradientDrawable.OVAL } }, LinearLayout.LayoutParams(dp(54), dp(54)).apply { rightMargin = dp(13) })
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(text("ScreenBubble", 25f, TEXT, true))
        brand.addView(text("Din AI-assistent", 13f, MUTED))
        logoRow.addView(brand)
        content.addView(logoRow)

        val hero = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(0xFF171D31.toInt(), 24); setPadding(dp(20), dp(22), dp(20), dp(22)) }
        hero.addView(text("Låt oss göra appen redo.", 25f, TEXT, true))
        hero.addView(text("Tre snabba steg. Sedan kan du använda ScreenBubble direkt ovanpå dina andra appar.", 14f, MUTED).apply { setPadding(0, dp(7), 0, dp(15)) })
        val illustration = LinearLayout(this).apply { gravity = Gravity.CENTER; background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF272047.toInt(), 0xFF111A2A.toInt())).apply { cornerRadius = dp(18).toFloat() }; setPadding(dp(12), dp(15), dp(12), dp(15)) }
        val phone = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(0xFF0B0E18.toInt(), 18); setPadding(dp(10), dp(10), dp(10), dp(10)) }
        phone.addView(text("Din skärm", 11f, MUTED, true))
        phone.addView(text("Hej! Kan du hjälpa mig?", 12f, TEXT).apply { background = rounded(0xFF252D43.toInt(), 10); setPadding(dp(10), dp(8), dp(10), dp(8)) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        phone.addView(text("Självklart. Jag hjälper dig här.", 12f, 0xFFBFFFE9.toInt()).apply { background = rounded(0xFF17352F.toInt(), 10); setPadding(dp(10), dp(8), dp(10), dp(8)) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        illustration.addView(phone, LinearLayout.LayoutParams(dp(230), -2))
        hero.addView(illustration, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        content.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        val serviceOn = ReaderService.instance != null
        val micOn = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val keyOn = !Config.embeddedKey().isEmpty() || !prefs.getString("api_key", "").isNullOrBlank()
        fun setupCard(title: String, detail: String, status: Boolean, action: String, click: () -> Unit) {
            val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(CARD, 17); setPadding(dp(15), dp(13), dp(15), dp(13)) }
            val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            top.addView(text(if (status) "✓" else "○", 19f, if (status) ACCENT else MUTED, true), LinearLayout.LayoutParams(dp(30), -2))
            top.addView(text(title, 15f, TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(text(if (status) "Klart" else "Behövs", 11f, if (status) ACCENT else MUTED, true))
            c.addView(top)
            c.addView(text(detail, 12.5f, MUTED).apply { setPadding(dp(30), dp(3), 0, dp(9)) })
            if (!status) c.addView(button(action, false, click), LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(30) })
            content.addView(c, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        }
        content.addView(text("Gör klart installationen", 18f, TEXT, true).apply { setPadding(0, dp(18), 0, dp(2)) })
        setupCard("Aktivera ScreenBubble", "Öppna appinformationen. På Android 13+ väljer du ⋮ och Tillåt begränsade inställningar. Gå sedan till Tillgänglighet och slå på ScreenBubble.", serviceOn, "Öppna inställningar") { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        setupCard("Tillåt mikrofonen", "Behövs för Diktera och röststyrning. Mikrofonen används bara när du startar en röstfunktion.", micOn, "Tillåt mikrofon") { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2) }

        if (Config.embeddedKey().isEmpty()) {
            val keyCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(CARD, 17); setPadding(dp(15), dp(13), dp(15), dp(13)) }
            val keyHead = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            keyHead.addView(text(if (keyOn) "✓" else "○", 19f, if (keyOn) ACCENT else MUTED, true), LinearLayout.LayoutParams(dp(30), -2))
            keyHead.addView(text("Anslut din AI-nyckel", 15f, TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
            keyHead.addView(text(if (keyOn) "Klart" else "Behövs", 11f, if (keyOn) ACCENT else MUTED, true))
            keyCard.addView(keyHead)
            keyCard.addView(text("ScreenBubble använder din OpenAI API-nyckel för AI-svar och för att finskriva dikteringar. Nyckeln sparas bara lokalt på enheten.", 12.5f, MUTED).apply { setPadding(dp(30), dp(3), 0, dp(8)) })
            val field = EditText(this).apply { hint = "sk-…"; setHintTextColor(MUTED); setTextColor(TEXT); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; background = rounded(0xFF222A3D.toInt(), 12); setPadding(dp(12), dp(11), dp(12), dp(11)); setText(prefs.getString("api_key", "")) }
            keyInput = field
            keyCard.addView(field, LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(30) })
            content.addView(keyCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        }
        content.addView(text("Du kan ändra utseende, röst och övriga inställningar när du är klar.", 12f, MUTED).apply { setPadding(dp(2), dp(13), dp(2), dp(7)) })
        val finish = button("Slutför och öppna appen", true) {
            saveKey()
            val readyService = ReaderService.instance != null
            val readyMic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val readyKey = !Config.embeddedKey().isEmpty() || !prefs.getString("api_key", "").isNullOrBlank()
            if (!readyService || !readyMic || !readyKey) toast("Slutför stegen ovan först.") else {
                prefs.edit().putBoolean("onboarding_complete", true).apply()
                activeTab = "home"
                render()
            }
        }
        finish.isEnabled = serviceOn && micOn && keyOn
        content.addView(finish, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    // ------------------------------------------------------------------ custom command dialog

    private fun commandDialog(c: Store.Cmd?) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val emoji = EditText(this).apply { hint = "Emoji (valfri)"; setText(c?.emoji ?: ""); filters = arrayOf(android.text.InputFilter.LengthFilter(4)) }
        val name = EditText(this).apply { hint = "Namn, t.ex. Svara artigt"; setText(c?.name ?: ""); filters = arrayOf(android.text.InputFilter.LengthFilter(30)) }
        val prompt = EditText(this).apply {
            hint = "Instruktion, t.ex. Skriv ett artigt svar på svenska till meddelandet på skärmen."
            setText(c?.prompt ?: ""); minLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val screen = CheckBox(this).apply { text = "Använd skärmen"; isChecked = c?.screen ?: true }
        box.addView(emoji); box.addView(name); box.addView(prompt); box.addView(screen)
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle(if (c == null) "Nytt snabbkommando" else "Redigera snabbkommando")
            .setView(box)
            .setPositiveButton("Spara") { _, _ ->
                val n = name.text.toString().trim().take(30)
                val p = prompt.text.toString().trim().take(800)
                if (n.isNotEmpty() && p.isNotEmpty()) {
                    val e = emoji.text.toString().trim().let { if (it.isEmpty()) "⚡" else String(Character.toChars(it.codePointAt(0))) }
                    Store.saveCommand(this, Store.Cmd(c?.id ?: "c" + System.currentTimeMillis(), e, n, p, screen.isChecked))
                    render()
                } else toast("Fyll i namn och instruktion")
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    // ------------------------------------------------------------------ updates

    private fun checkUpdate(manual: Boolean) {
        Thread {
            val l = Updater.latest()
            val cur = Updater.current(this)
            runOnUiThread {
                if (l != null && l > cur) {
                    newerBuild = l
                    if (!updating) {
                        updateText = null
                        render()
                        // Start the update immediately when Android permits installs.
                        // Android still requires the system confirmation dialog.
                        if (Updater.canInstall(this)) startUpdate()
                    }
                } else if (manual) toast(if (l == null) "Kunde inte söka just nu" else "Du har senaste versionen")
            }
        }.start()
    }

    private fun startUpdate() {
        if (!Updater.canInstall(this)) {
            toast("Tillåt installation, gå tillbaka och tryck igen")
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        updating = true
        updateText = "Hämtar… 0 %"
        render()
        Thread {
            try {
                Updater.downloadAndInstall(this) { pct ->
                    runOnUiThread { updateText = "Hämtar… $pct %"; render() }
                }
                runOnUiThread { updateText = "Klart att installera. Bekräfta i rutan som visas."; updating = false; render() }
            } catch (e: Exception) {
                runOnUiThread { updateText = "Misslyckades: ${e.message}"; updating = false; render() }
            }
        }.start()
    }
}
