package com.example.screenbubble

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
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
        root.addView(TextView(this).apply {
            text = "En flytande AI-bubbla som ser din skärm."
            textSize = 15f
            setPadding(0, pad / 2, 0, pad)
        })

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
            text = "Starta bubblan"
            setOnClickListener { startBubble() }
        })
        root.addView(Button(this).apply {
            text = "Stoppa bubblan"
            setOnClickListener {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
            }
        })

        root.addView(TextView(this).apply {
            text = "Så använder du den\n\n" +
                "• Tryck på bubblan: öppnar chatten (med \"Se skärmen\" ser AI:n det du har framför dig).\n" +
                "• Håll in bubblan: snabbsvar. AI:n läser frågor och quiz på skärmen och visar bara rätt svar i en liten ruta som försvinner av sig själv. Tryck på rutan för att stänga den direkt.\n" +
                "• Dra bubblan dit du vill ha den.\n\n" +
                "Första gången frågar Android om tillåtelse att visa över andra appar och om skärmdelning. Välj \"Hela skärmen\". " +
                "Skärmbilder skickas till OpenAI när funktionen används."
            textSize = 14f
            setPadding(0, pad, 0, 0)
        })

        setContentView(root)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun startBubble() {
        val et = keyInput
        if (et != null) {
            val key = et.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "Ange din OpenAI API-nyckel först", Toast.LENGTH_SHORT).show()
                return
            }
            getSharedPreferences("prefs", MODE_PRIVATE).edit().putString("api_key", key).apply()
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Slå på \"Visa över andra appar\" för ScreenBubble och kom sedan tillbaka",
                Toast.LENGTH_LONG
            ).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                val intent = Intent(this, OverlayService::class.java)
                    .putExtra(OverlayService.EXTRA_CODE, resultCode)
                    .putExtra(OverlayService.EXTRA_DATA, data)
                startForegroundService(intent)
                moveTaskToBack(true)
            } else {
                Toast.makeText(this, "Skärmdelning nekades", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQ_CAPTURE = 100
    }
}
