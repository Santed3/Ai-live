package com.example.screenbubble

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private var keyInput: EditText? = null
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            setBackgroundColor(Color.WHITE)
        }

        root.addView(TextView(this).apply {
            text = "ScreenBubble"
            textSize = 28f
            setTextColor(Color.BLACK)
        })

        status = TextView(this).apply {
            textSize = 14f
            setPadding(0, pad / 2, 0, pad / 2)
        }
        root.addView(status)

        if (Config.embeddedKey().isEmpty()) {
            val et = EditText(this).apply {
                hint = "OpenAI API-nyckel (sk-...)"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setText(getSharedPreferences("prefs", MODE_PRIVATE).getString("api_key", ""))
            }
            keyInput = et
            root.addView(et)
        }

        root.addView(Button(this).apply {
            text = "1. Tillåt begränsade inställningar (appinfo)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        })
        root.addView(Button(this).apply {
            text = "2. Slå på ScreenBubble i Tillgänglighet"
            setOnClickListener {
                saveKey()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })
        root.addView(Button(this).apply {
            text = "3. Tillåt mikrofon (för röst)"
            setOnClickListener { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        })
        root.addView(Button(this).apply {
            text = "Öppna sidopanelen"
            setOnClickListener { saveKey(); ReaderService.instance?.setMode(ReaderService.Mode.PANEL) }
        })
        root.addView(Button(this).apply {
            text = "Hide-läge (bara svart prick)"
            setOnClickListener { saveKey(); ReaderService.instance?.setMode(ReaderService.Mode.HIDE) }
        })

        val help = TextView(this).apply {
            text = "Så funkar det\n\n" +
                "Sidopanelen: fråga om det som är på skärmen, be assistenten öppna appar, trycka, skriva, söka på webben, ställa alarm och timers, eller föreslå svar på mejl och meddelanden. " +
                "Använd 🎤 för röst och Samtal för fri konversation. Skicka och andra känsliga åtgärder kräver alltid att du bekräftar.\n\n" +
                "Hide-läge: tryck Dölj i panelen. Då visas bara en liten svart prick. Ett tryck ger ett kort svar på frågor på skärmen, håll in pricken för att öppna panelen, dubbeltryck stänger av.\n\n" +
                "Android 13 och nyare: tryck ⋮ i appinfo och välj \"Tillåt begränsade inställningar\" före steg 2.\n\n" +
                "Skärmens innehåll skickas till OpenAI när du ställer en fråga. Använd inte appen där hjälpmedel är förbjudna, till exempel på prov."
            textSize = 14f
            setPadding(0, pad, 0, 0)
        }
        root.addView(help)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onPause() {
        saveKey()
        super.onPause()
    }

    private fun saveKey() {
        val et = keyInput ?: return
        getSharedPreferences("prefs", MODE_PRIVATE).edit().putString("api_key", et.text.toString().trim()).apply()
    }

    private fun refreshStatus() {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val on = ReaderService.instance != null
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val err = prefs.getString("last_error", "").orEmpty()
        status.text = (if (on) "Tjänst: på" else "Tjänst: av (slå på i Tillgänglighet)") +
            "\nMikrofon: " + (if (mic) "tillåten" else "inte tillåten") +
            (if (err.isNotEmpty()) "\nSenaste fel: $err" else "")
    }
}
