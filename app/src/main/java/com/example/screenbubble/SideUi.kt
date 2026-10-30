package com.example.screenbubble

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import kotlin.math.abs

/** The slim sidebar handle and the quick-command menu. */
class SideUi(private val svc: ReaderService) {
    private val density = svc.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private fun rounded(color: Int, r: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(color, 0xF50B0E16.toInt())).apply {
        cornerRadius = dp(r).toFloat()
        setStroke(dp(1), 0x22FFFFFF)
    }

    /** The thin handle at the screen edge. Drag to move up/down, tap to open the menu. */
    @SuppressLint("ClickableViewAccessibility")
    fun bar(lp: WindowManager.LayoutParams, onMove: () -> Unit): View {
        val frame = FrameLayout(svc)
        val pill = View(svc).apply { background = rounded(0xE6202226.toInt(), 4) }
        frame.addView(pill, FrameLayout.LayoutParams(dp(6), dp(56), Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(1) })
        pill.alpha = 0f
        pill.scaleY = 0.82f
        pill.animate().alpha(1f).scaleY(1f).setDuration(220)
            .setInterpolator(DecelerateInterpolator()).start()
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

    /** Minimal branded floating Smart Reply indicator with animated dots. */
    fun suggestionLoading(): View {
        val row = LinearLayout(svc).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xF2141722.toInt(), 24)
            setPadding(dp(10), dp(7), dp(13), dp(7))
            elevation = dp(9).toFloat()
        }
        row.addView(TextView(svc).apply {
            text = "S"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF9D86FF.toInt(), 0xFF6345E8.toInt())).apply { shape = GradientDrawable.OVAL }
        }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(8) })
        row.addView(TextView(svc).apply {
            text = "ScreenBubble"
            textSize = 11.5f
            setTextColor(0xFFE8EAF4.toInt())
        })
        val dots = LinearLayout(svc).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(7), 0, 0, 0) }
        val dotAnimations = mutableListOf<ObjectAnimator>()
        repeat(3) { i ->
            val dot = TextView(svc).apply {
                text = "•"
                textSize = 14f
                setTextColor(0xFFAB96FF.toInt())
            }
            dots.addView(dot)
            dotAnimations.add(ObjectAnimator.ofFloat(dot, "translationY", 0f, -dp(3).toFloat(), 0f).apply {
                duration = 760
                startDelay = i * 130L
                repeatCount = ValueAnimator.INFINITE
                start()
            })
        }
        row.addView(dots)
        row.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { dotAnimations.forEach { it.cancel() } }
        })
        return row
    }

    /** Tiny reply choices with a ScreenBubble mark; tapping inserts the text into the active field. */
    fun suggestions(list: List<String>, title: String = "Förslag på svar", onPick: (String) -> Unit, onClose: () -> Unit): View {
        val card = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xF2141722.toInt(), 18)
            setPadding(dp(10), dp(8), dp(10), dp(9))
            elevation = dp(9).toFloat()
        }
        val head = LinearLayout(svc).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(svc).apply {
            text = "S"
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF9D86FF.toInt(), 0xFF6345E8.toInt())).apply { shape = GradientDrawable.OVAL }
        }, LinearLayout.LayoutParams(dp(19), dp(19)).apply { rightMargin = dp(7) })
        head.addView(TextView(svc).apply {
            text = "ScreenBubble  ·  $title"
            textSize = 10.5f
            setTextColor(0xFFB9C0D3.toInt())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(TextView(svc).apply {
            text = "×"
            textSize = 17f
            setTextColor(0xFF8993AA.toInt())
            setPadding(dp(8), 0, dp(2), 0)
            setOnClickListener { onClose() }
        })
        card.addView(head)
        for (s in list.take(3)) {
            card.addView(TextView(svc).apply {
                text = s
                textSize = 13f
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(0xFFECEFF8.toInt())
                background = GradientDrawable().apply {
                    setColor(0x1F8C70FF)
                    cornerRadius = dp(11).toFloat()
                    setStroke(dp(1), 0x287F68FF)
                }
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setOnClickListener { onPick(s) }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
        return card
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
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xF51C2233.toInt(), 0xF50B0E16.toInt())).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), 0x2AFFFFFF)
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            elevation = dp(10).toFloat()
        }
        card.alpha = 0f
        card.translationY = dp(8).toFloat()
        card.animate().alpha(1f).translationY(0f).setDuration(230)
            .setInterpolator(DecelerateInterpolator()).start()
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
            "AI-lägen" to listOf(
                Tile("⚡", "AI Actions") { svc.openChat("Hjälp mig genomföra uppgiften på skärmen steg för steg. Bekräfta innan du skickar, raderar, köper eller publicerar.", true) },
                Tile("🌐", "Smart Translate") { svc.openChat("Översätt den viktigaste texten på skärmen till svenska och behåll struktur och ton.", true) },
                Tile("🎓", "Study Mode") { svc.openChat("Förklara uppgiften pedagogiskt steg för steg. Ge ledtrådar innan hela lösningen och kontrollera min förståelse.", true) },
                Tile("✍️", "Writing Mode") { svc.openChat("Hjälp mig skriva eller förbättra texten på skärmen. Matcha sammanhang och min skrivstil.", true) },
                Tile("🔎", "Web Research") { svc.openChat("Undersök ämnet på skärmen, jämför tillförlitliga källor och sammanfatta med länkar.", true) },
                Tile("🧩", "App Profiles") { openApp(null) }
            ),
            "Verktyg" to listOf(
                Tile("🎤", "Röst") { svc.openChat(null, false); svc.voiceSoon() },
                Tile("🧠", "Minne") { svc.openChat("visa minne", true) },
                Tile("🫥", "Hide-läge") { svc.setMode(ReaderService.Mode.HIDE) },
                Tile("⬇️", "Uppdatera") { openApp("checkupdate") },
                Tile("💡", "Förslag: " + (if (Store.flag(svc, "smart_suggest")) "på" else "av")) {
                    Store.setFlag(svc, "smart_suggest", !Store.flag(svc, "smart_suggest"))
                    svc.setMode(ReaderService.Mode.MENU)
                }
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
                    background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(if (t.icon == "＋") 0x12FFFFFF else 0x1CFFFFFF, 0x0DFFFFFF)).apply {
                        cornerRadius = dp(16).toFloat()
                        setStroke(dp(1), if (t.icon == "＋") 0x44FFFFFF else 0x16FFFFFF)
                    }
                    setPadding(dp(4), dp(8), dp(4), dp(6))
                    isClickable = true
                    isFocusable = true
                    setOnTouchListener { v, e ->
                        when (e.actionMasked) {
                            MotionEvent.ACTION_DOWN -> v.animate().scaleX(.97f).scaleY(.97f).setDuration(90).start()
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f)
                                .setDuration(150).setInterpolator(DecelerateInterpolator()).start()
                        }
                        false
                    }
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
