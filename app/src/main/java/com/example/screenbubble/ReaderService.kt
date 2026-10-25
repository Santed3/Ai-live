package com.example.screenbubble

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Accessibility service that shows one tiny black dot over all apps.
 * One tap: reads the text on screen (no screen sharing), asks OpenAI, shows only the short answer
 * next to the dot for ANSWER_MS. Double tap: removes the dot and switches the service off.
 */
class ReaderService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private var dot: View? = null
    private var dotLp: WindowManager.LayoutParams? = null
    private var answerView: View? = null
    private var busy = false
    private var closing = false
    private var requestId = 0
    private var lastTap = 0L
    private var screenW = 0
    private var screenH = 0

    private val answerTimeout = Runnable { finishAnswer() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        closing = false
        busy = false
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val b = wm.currentWindowMetrics.bounds
        screenW = b.width()
        screenH = b.height()
        showDot()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        main.removeCallbacks(answerTimeout)
        removeAnswer()
        hideDot()
        worker.shutdownNow()
        return super.onUnbind(intent)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ------------------------------------------------------------------- dot

    fun setDotVisible(visible: Boolean) {
        main.post { if (visible) showDot() else hideDot() }
    }

    private fun showDot() {
        if (dot != null || busy) return
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val hit = dp(36)
        val lp = WindowManager.LayoutParams(
            hit, hit,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("dot_x", screenW - hit - dp(8))
            y = prefs.getInt("dot_y", screenH / 3)
        }

        val frame = FrameLayout(this)
        val circle = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.BLACK)
            }
        }
        frame.addView(circle, FrameLayout.LayoutParams(dp(12), dp(12), Gravity.CENTER))

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        frame.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = e.rawX; touchY = e.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        lp.x = min(max(0, startX + dx), screenW - hit)
                        lp.y = min(max(0, startY + dy), screenH - hit)
                        wm.updateViewLayout(frame, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        prefs.edit().putInt("dot_x", lp.x).putInt("dot_y", lp.y).apply()
                    } else {
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastTap < DOUBLE_TAP_MS) {
                            lastTap = 0
                            shutDown()
                        } else {
                            lastTap = now
                            onDotTap()
                        }
                    }
                    true
                }
                else -> false
            }
        }
        wm.addView(frame, lp)
        dot = frame
        dotLp = lp
    }

    private fun hideDot() {
        dot?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        dot = null
    }

    // ---------------------------------------------------------------- answer

    private fun onDotTap() {
        if (busy) return
        val key = Config.embeddedKey().ifEmpty {
            getSharedPreferences("prefs", MODE_PRIVATE).getString("api_key", "").orEmpty()
        }
        val x = dotLp?.x ?: 0
        val y = dotLp?.y ?: 0
        busy = true
        val id = ++requestId
        if (key.isEmpty()) {
            saveError("Ingen API-nyckel. Skriv in den i appen.")
            showAnswer("!", x, y)
            return
        }
        worker.execute {
            try {
                val screenText = readScreenText()
                val shot = takeShot()
                if (screenText.isBlank() && shot == null) {
                    throw RuntimeException("Kunde inte läsa skärmen (skyddad skärm?)")
                }
                val answer = OpenAiClient(key).askOnce(screenText, shot)
                main.post { if (id == requestId && !closing) showAnswer(answer, x, y) }
            } catch (e: Exception) {
                saveError(e.message ?: "Okänt fel")
                main.post { if (id == requestId && !closing) showAnswer("!", x, y) }
            }
        }
    }

    /** Double tap: remove everything and switch the service off. */
    private fun shutDown() {
        closing = true
        requestId++
        main.removeCallbacks(answerTimeout)
        removeAnswer()
        hideDot()
        busy = false
        disableSelf()
    }

    private fun showAnswer(msg: String, x: Int, y: Int) {
        removeAnswer()
        hideDot()
        val tv = TextView(this).apply {
            text = msg.replace("**", "").trim()
            textSize = 13f
            setTextColor(0xFFC8C8C8.toInt())
            setPadding(dp(6), dp(3), dp(6), dp(3))
            background = GradientDrawable().apply {
                setColor(0xE6000000.toInt())
                cornerRadius = dp(6).toFloat()
            }
            maxWidth = (screenW * 0.7f).toInt()
            setOnClickListener { finishAnswer() }
        }
        tv.measure(
            View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
        )
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val hit = dp(36)
            val wantX = if (x > screenW / 2) x - tv.measuredWidth else x + hit
            this.x = min(max(0, wantX), max(0, screenW - tv.measuredWidth))
            this.y = min(max(0, y + (hit - tv.measuredHeight) / 2), max(0, screenH - tv.measuredHeight))
        }
        wm.addView(tv, lp)
        answerView = tv
        main.removeCallbacks(answerTimeout)
        val lines = msg.trim().lines().size
        main.postDelayed(answerTimeout, min(ANSWER_MAX_MS, ANSWER_MS + (lines - 1) * 1000L))
    }

    private fun removeAnswer() {
        answerView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        answerView = null
    }

    private fun finishAnswer() {
        main.removeCallbacks(answerTimeout)
        removeAnswer()
        busy = false
        showDot()
    }

    private fun saveError(msg: String) {
        getSharedPreferences("prefs", MODE_PRIVATE).edit().putString("last_error", msg).apply()
    }

    // ---------------------------------------------------------- read screen

    /** Collects the visible text of the active window from the accessibility tree. */
    private fun readScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        var count = 0
        var last = ""

        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || count > 1500 || sb.length > 8000) return
            count++
            if (n.packageName?.toString() == packageName) return
            if (n.isVisibleToUser) {
                val t = n.text?.toString()?.trim().orEmpty()
                val d = n.contentDescription?.toString()?.trim().orEmpty()
                val s = if (t.isNotEmpty()) t else d
                if (s.isNotEmpty() && s != last) {
                    sb.append(s)
                    if (n.isChecked || n.isSelected) sb.append(" [vald]")
                    sb.append('\n')
                    last = s
                }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }

        walk(root)
        return sb.toString().take(8000)
    }

    /** Fallback when the screen has little readable text: silent screenshot, no screen-sharing prompt. */
    private fun takeShot(): String? {
        val latch = CountDownLatch(1)
        var out: String? = null
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    try {
                        val hw = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        screenshot.hardwareBuffer.close()
                        if (bmp != null) out = encode(bmp)
                    } finally {
                        latch.countDown()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    latch.countDown()
                }
            }
        )
        latch.await(5, TimeUnit.SECONDS)
        return out
    }

    private fun encode(bmp: Bitmap): String {
        val scale = min(1f, 1800f / max(bmp.width, bmp.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        } else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        /** How long the answer stays visible. */
        const val ANSWER_MS = 2000L
        const val ANSWER_MAX_MS = 6000L
        const val DOUBLE_TAP_MS = 350L

        @Volatile
        var instance: ReaderService? = null
    }
}
