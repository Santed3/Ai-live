package com.example.screenbubble

object Config {
    // Byts ut vid bygget om GitHub-secret OPENAI_API_KEY finns. Annars frågar appen efter nyckeln.
    private const val EMBEDDED = "__OPENAI_KEY__"

    fun embeddedKey(): String = if (EMBEDDED.startsWith("sk-")) EMBEDDED else ""
}
