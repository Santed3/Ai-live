package com.example.screenbubble

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Shows up as "Fråga ScreenBubble" in the text-selection menu of any app and sends the selection to the chat. */
class ProcessTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
        val svc = ReaderService.instance
        when {
            text.isEmpty() -> Toast.makeText(this, "Ingen text markerad", Toast.LENGTH_SHORT).show()
            svc == null -> {
                Toast.makeText(this, "Slå på ScreenBubble i Tillgänglighet först", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            else -> svc.openChat("Förklara eller svara kort på den här texten som jag markerade:\n\n" + text.take(4000), true)
        }
        finish()
    }
}
