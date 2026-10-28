package com.example.screenbubble

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** The slim sidebar handle and the quick-command menu. */
class SideUi(private val svc: ReaderService) {
    private val density = svc.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private fun rounded(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }

    /** The thin handle at the screen edge. Drag to move up/down, tap to open the menu. */
    @SuppressLint("ClickableViewAccessibility")
    fun bar(lp: WindowManager.LayoutParams, onMove: () -> Unit): View {
        val frame = FrameLayout(svc)
        val pill = View(svc).apply { background = rounded(0xE6202226.toInt(), 4) }
        frame.addView(pill, FrameLayout.LayoutParams(dp(6), dp(56), Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(1) })
        var sy = 0f; var oy = 0; var moved = false
        frame.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sy = e.rawY; oy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val d = e.rawY - sy
                    if (abs(d) > dp(6)) moved = true
                    if (moved) { lp.y = oy + d.toInt(); onMove() }
                }
                MotionEvent.ACTION_UP -> if (!moved) svc.setMode(ReaderService.Mode.MENU)
            }
            true
        }
        return frame
    }

    private class Tile(val icon: String, val label: String, val run: () -> Unit)

    fun menu(onOutside: () -> Unit): View {
        val card = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xF5121316.toInt(), 22)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            elevation = dp(10).toFloat()
        }
        card.addView(TextView(svc).apply {
            this.text = "ScreenBubble"
            textSize = 15f
            setTextColor(0xFFECEFF8.toInt())
            setPadding(dp(4), 0, 0, dp(8))
        })
        val M = ReaderService.Mode.CHAT
        val tiles = listOf(
            Tile("💬", "Chatt") { svc.openChat(null, false) },
            Tile("✅", "Svara på frågan") { svc.quickAnswer() },
            Tile("🧩", "Lös uppgiften") { svc.openChat("Lös uppgiften som syns på skärmen och utför den om det går.", true) },
            Tile("🔎", "Google") { svc.openChat("google ", false) },
            Tile("✍️", "Skriv") { svc.openChat("skriv ", false) },
            Tile("💌", "Svara på meddelande") { svc.openChat("Föreslå ett kort svar på meddelandet på skärmen.", true) },
            Tile("📄", "Sammanfatta") { svc.openChat("Sammanfatta det som syns på skärmen kort.", true) },
            Tile("🌍", "Översätt") { svc.openChat("Översätt texten på skärmen till svenska.", true) },
            Tile("🫥", "Hide-läge") { svc.setMode(ReaderService.Mode.HIDE) },
            Tile("🎤", "Röst") { svc.openChat(null, false); svc.voiceSoon() },
            Tile("➖", "Minimera") { svc.setMode(ReaderService.Mode.BAR) },
            Tile("✕", "Stäng") { svc.shutDown() }
        )
        val grid = GridLayout(svc).apply { columnCount = 3 }
        for (t in tiles) {
            val cell = LinearLayout(svc).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(0x1FFFFFFF, 16)
                setPadding(dp(4), dp(10), dp(4), dp(8))
                setOnClickListener { t.run() }
            }
            cell.addView(TextView(svc).apply { this.text = t.icon; textSize = 22f; gravity = Gravity.CENTER })
            cell.addView(TextView(svc).apply {
                this.text = t.label; textSize = 11f; gravity = Gravity.CENTER
                setTextColor(0xFFC9CEDF.toInt()); maxLines = 2
            })
            val glp = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
                width = 0; height = dp(72); setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            grid.addView(cell, glp)
        }
        card.addView(grid)
        card.setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_OUTSIDE) onOutside(); false }
        return card
    }
}
