package com.example.screenbubble

import android.content.Context
import okhttp3.Request
import java.util.UUID

/** Public client configuration only. The OpenAI secret stays in the Supabase Edge Function. */
object AiBackend {
    private const val SUPABASE_URL = "https://qkyeqaemcrfhtvmeiskg.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_aRSCLGxAGpiXWfdU6TNv9Q_zDRa0j2G"

    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences("screenbubble_backend", Context.MODE_PRIVATE)
        val existing = prefs.getString("installation_id", null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("installation_id", created).apply()
        return created
    }

    fun url(endpoint: String): String =
        "$SUPABASE_URL/functions/v1/screenbubble-ai?endpoint=$endpoint"
}
