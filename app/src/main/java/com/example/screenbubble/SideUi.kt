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

    /** ScrollView that never grows taller than [maxH], so a long menu scrolls instead of overflowing. */
    private class CappedScroll(ctx: android.content.Context, private val maxH: Int) : android.widget.ScrollView(ctx) {
        override fun onMeasure(w: Int, h: Int) {
            super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST))
        }
    }

    private fun openApp(extra: String?) {
        val i = android.content.Intent(svc, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (extra != null) i.putExtra(extra, true)
        svc.startActivity(i)
        svc.setMode(ReaderService.Mode.BAR)
    }

    fun menu(onOutside: () -> Unit): View {
        val card = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xF5121316.toInt(), 22)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            elevation = dp(10).toFloat()
        }
        val head = LinearLayout(svc).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), 0, 0, dp(2)) }
        head.addView(View(svc).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFB9A8FF.toInt(), 0xFF6C47FF.toInt())).apply { shape = GradientDrawable.OVAL }
        }, LinearLayout.LayoutParams(dp(9), dp(9)).apply { rightMargin = dp(8) })
        head.addView(TextView(svc).apply {
            this.text = "ScreenBubble"
            textSize = 15f
            setTextColor(0xFFECEFF8.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        card.addView(head)

        val groups = listOf(
            "Svara" to listOf(
                Tile("💬", "Chatt") { svc.openChat(null, false) },
                Tile("✅", "Svara på frågan") { svc.quickAnswer() },
                Tile("💌", "Svara på meddelande") { svc.openChat("Föreslå ett kort svar på meddelandet på skärmen.", true) },
                Tile("🧩", "Lös uppgiften") { svc.openChat("Lös uppgiften som syns på skärmen och utför den om det går.", true) }
            ),
            "Skriv och sök" to listOf(
                Tile("🔎", "Google") { svc.openChat("google ", false) },
                Tile("✍️", "Skriv") { svc.openChat("skriv ", false) },
                Tile("📄", "Sammanfatta") { svc.openChat("Sammanfatta det som syns på skärmen kort.", true) },
                Tile("🌍", "Översätt") { svc.openChat("Översätt texten på skärmen till svenska.", true) }
            ),
            "Verktyg" to listOf(
                Tile("🎤", "Röst") { svc.openChat(null, false); svc.voiceSoon() },
                Tile("🧠", "Minne") { svc.openChat("visa minne", true) },
                Tile("🫥", "Hide-läge") { svc.setMode(ReaderService.Mode.HIDE) },
                Tile("⬇️", "Uppdatera") { openApp("checkupdate") }
            )
        ).toMutableList()
        val mine = Store.commands(svc).map { c ->
            Tile(c.emoji, c.name) {
                svc.openChat(if (c.screen) c.prompt else c.prompt + "\n\n(Du behöver inte titta på skärmen.)", true)
            }
        } + Tile("＋", "Nytt kommando") { openApp("newcmd") }
        groups.add("Egna kommandon" to mine)
        groups.add(
            "System" to listOf(
                Tile("➖", "Minimera") { svc.setMode(ReaderService.Mode.BAR) },
                Tile("✕", "Stäng") { svc.shutDown() }
            )
        )

        val list = LinearLayout(svc).apply { orientation = LinearLayout.VERTICAL }
        for ((title, tiles) in groups) {
            list.addView(TextView(svc).apply {
                this.text = title.uppercase()
                textSize = 10.5f
                letterSpacing = 0.08f
                setTextColor(0xFF8D95AD.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(4), dp(10), 0, dp(4))
            })
            val grid = GridLayout(svc).apply { columnCount = 3 }
            for (t in tiles) {
                val cell = LinearLayout(svc).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = rounded(if (t.icon == "＋") 0x0AFFFFFF else 0x1AFFFFFF, 16)
                    setPadding(dp(4), dp(8), dp(4), dp(6))
                    setOnClickListener { t.run() }
                }
                cell.addView(TextView(svc).apply { this.text = t.icon; textSize = 21f; gravity = Gravity.CENTER })
                cell.addView(TextView(svc).apply {
                    this.text = t.label; textSize = 11f; gravity = Gravity.CENTER
                    setTextColor(0xFFC9CEDF.toInt()); maxLines = 2
                })
                val glp = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
                    width = 0; height = dp(66); setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                grid.addView(cell, glp)
            }
            list.addView(grid)
        }
        val maxH = (svc.resources.displayMetrics.heightPixels * 0.72f).toInt()
        card.addView(CappedScroll(svc, maxH).apply { isVerticalScrollBarEnabled = false; addView(list) })
        card.setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_OUTSIDE) onOutside(); false }
        return card
    }
}
