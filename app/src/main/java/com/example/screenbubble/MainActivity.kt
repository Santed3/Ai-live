package com.example.screenbubble

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

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
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
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
            text = "Visa knappen"
            setOnClickListener { saveKey(); ReaderService.instance?.setDotVisible(true) }
        })
        root.addView(Button(this).apply {
            text = "Dölj knappen"
            setOnClickListener { ReaderService.instance?.setDotVisible(false) }
        })

        root.addView(TextView(this).apply {
            text = "Så funkar det\n\n" +
                "• En liten svart prick ligger över alla appar. Dra den dit du vill ha den.\n" +
                "• Ett tryck: skärmens text läses (ingen skärmdelning) och bara rätt svar visas bredvid pricken i 2 sekunder.\n" +
                "• Dubbeltryck: pricken försvinner och tjänsten stängs av. Slå på igen i Tillgänglighet.\n\n" +
                "Android 13 och nyare: tryck ⋮ i appinfo och välj \"Tillåt begränsade inställningar\" först (steg 1)."
            textSize = 14f
            setPadding(0, pad, 0, 0)
        })

        setContentView(root)
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
        getSharedPreferences("prefs", MODE_PRIVATE).edit()
            .putString("api_key", et.text.toString().trim()).apply()
    }

    private fun refreshStatus() {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val on = ReaderService.instance != null
        val err = prefs.getString("last_error", "").orEmpty()
        status.text = (if (on) "Status: på (pricken visas)" else "Status: av (slå på i Tillgänglighet)") +
            (if (err.isNotEmpty()) "\nSenaste fel: $err" else "")
    }
}
