package com.example.screenbubble

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.AlarmClock
import android.util.Base64
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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
 * Host service. Two modes:
 *  - PANEL: a side panel (chat, shortcuts, voice, phone control).
 *  - HIDE: only a tiny black dot. Tap = quick answer, hold = back to panel, double tap = close.
 */
class ReaderService : AccessibilityService() {

    enum class Mode { BAR, MENU, CHAT, HIDE }

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    val pool = Executors.newCachedThreadPool()

    private var mode = Mode.BAR
    private var side: View? = null
    private var sideLp: WindowManager.LayoutParams? = null
    private var barY = 0
    private val smart by lazy { SmartReply(this) }
    private var sugView: View? = null
    private var sugLoadingView: View? = null
    private var hintHide = false
    private var ready = false
    private var panel: PanelUi? = null
    private var dot: View? = null
    private var dotLp: WindowManager.LayoutParams? = null
    private var answerView: View? = null
    private var dotBusy = false
    private var closing = false
    private var requestId = 0
    private var lastTap = 0L
    private var lastPkg = ""
    var screenW = 0
    var screenH = 0

    private val answerTimeout = Runnable { finishAnswer() }

    fun apiKey(): String = Config.embeddedKey().ifEmpty {
        getSharedPreferences("prefs", MODE_PRIVATE).getString("api_key", "").orEmpty()
    }

    // ------------------------------------------------------------ lifecycle

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        closing = false
        dotBusy = false
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val b = wm.currentWindowMetrics.bounds
        screenW = b.width()
        screenH = b.height()
        setMode(if (Store.mode(this) == "HIDE") Mode.HIDE else Mode.BAR)
        main.post { ready = true }
        pool.execute {
            val n = Updater.checkIfDue(this)
            if (n != null) main.post {
                android.widget.Toast.makeText(this, "Ny version av ScreenBubble finns. Öppna appen för att uppdatera.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        try { smart.onEvent(event) } catch (_: Exception) {}
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val p = event.packageName?.toString() ?: return
            if (p != packageName && p != "com.android.systemui" && !p.contains("inputmethod")) lastPkg = p
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        closing = true
        main.removeCallbacksAndMessages(null)
        removeAnswer()
        removePanel()
        removeSide()
        hideDot()
        pool.shutdownNow()
        return super.onUnbind(intent)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ----------------------------------------------------------------- modes

    fun setMode(m: Mode) {
        main.post {
            if (closing) return@post
            val prev = mode
            mode = m
            if (m != Mode.BAR) removeSuggestions()
            hintHide = m == Mode.HIDE && prev != Mode.HIDE && ready
            if (m == Mode.BAR || m == Mode.HIDE) Store.setMode(this, m.name)
            when (m) {
                Mode.BAR -> { hideDot(); removePanel(); showBar() }
                Mode.MENU -> { hideDot(); removePanel(); showMenu() }
                Mode.CHAT -> { hideDot(); removeAnswer(); removeSide(); showPanel() }
                Mode.HIDE -> { removePanel(); removeSide(); showDot(); if (hintHide) main.postDelayed({ hideHint() }, 250) }
            }
        }
    }

    private var pendingVoice = false
    fun voiceSoon() { pendingVoice = true }

    /** Open the compact chat, optionally with a preset message. */
    fun openChat(preset: String?, auto: Boolean) {
        setMode(Mode.CHAT)
        main.post {
            panel?.start(preset, auto)
            if (pendingVoice) { pendingVoice = false; panel?.startVoice() }
        }
    }

    /** Menu shortcut: answer the question on screen, shown as a small card next to the handle. */
    fun quickAnswer() {
        setMode(Mode.BAR)
        main.postDelayed({ onDotTap(screenW, screenH / 2 + barY) }, 150)
    }

    private fun removeSide() {
        side?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        side = null
    }

    private fun showBar() {
        removeSide()
        val lp = WindowManager.LayoutParams(
            dp(22), dp(96),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; y = barY }
        val v = SideUi(this).bar(lp) { barY = lp.y; try { wm.updateViewLayout(side, lp) } catch (_: Exception) {} }
        wm.addView(v, lp)
        side = v; sideLp = lp
    }

    private fun showMenu() {
        removeSide()
        val lp = WindowManager.LayoutParams(
            min(dp(300), (screenW * 0.88f).toInt()), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = dp(8); y = barY }
        val v = SideUi(this).menu { setMode(Mode.BAR) }
        wm.addView(v, lp)
        side = v; sideLp = lp
    }

    fun shutDown() {
        main.post {
            closing = true
            requestId++
            main.removeCallbacks(answerTimeout)
            removeAnswer()
            removePanel()
            removeSide()
            hideDot()
            disableSelf()
        }
    }

    private fun showPanel() {
        if (panel != null) return
        val p = PanelUi(this)
        val w = min(dp(320), (screenW * 0.86f).toInt())
        val h = min(dp(400), (screenH * 0.46f).toInt())
        val lp = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(8)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        wm.addView(p.root, lp)
        panel = p
        p.onShown()
    }

    private fun removePanel() {
        panel?.let {
            it.destroy()
            try { wm.removeView(it.root) } catch (_: Exception) {}
        }
        panel = null
    }

    // ------------------------------------------------------------------- dot

    private fun showDot() {
        if (dot != null || dotBusy) return
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val hit = dp(36)
        val lp = WindowManager.LayoutParams(
            hit, hit,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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
                setStroke(dp(1), 0x77FFFFFF)
            }
        }
        frame.addView(circle, FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER))

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        var longFired = false
        val longPress = Runnable {
            if (!moved) {
                longFired = true
                frame.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                setMode(Mode.BAR)
            }
        }
        frame.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = e.rawX; touchY = e.rawY
                    moved = false
                    longFired = false
                    main.postDelayed(longPress, 450)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) {
                        moved = true
                        main.removeCallbacks(longPress)
                    }
                    if (moved) {
                        lp.x = min(max(0, startX + dx), screenW - hit)
                        lp.y = min(max(0, startY + dy), screenH - hit)
                        wm.updateViewLayout(frame, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(longPress)
                    if (e.action == MotionEvent.ACTION_UP && !longFired) {
                        if (moved) {
                            prefs.edit().putInt("dot_x", lp.x).putInt("dot_y", lp.y).apply()
                        } else {
                            val now = SystemClock.uptimeMillis()
                            if (now - lastTap < DOUBLE_TAP_MS) {
                                lastTap = 0
                                shutDown()
                            } else {
                                lastTap = now
                                onDotTap()
                            }
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

    /** Short card next to the dot that explains hide mode and how to get back to the sidebar. */
    private fun hideHint() {
        val l = dotLp ?: return
        showAnswer("Hide-läge på\nHåll in pricken för sidebaren", l.x, l.y)
    }

    fun isHidden() = mode == Mode.HIDE

    // ------------------------------------------------------- reply suggestions

    fun canShowSuggestions() = mode == Mode.BAR

    fun hideSuggestions() = main.post { removeSuggestions() }

    private fun removeSuggestions() {
        sugView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        sugView = null
        sugLoadingView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        sugLoadingView = null
    }

    /** Tiny ScreenBubble typing indicator, floating over the current app. */
    fun showSuggestionLoading() {
        if (mode != Mode.BAR || closing) return
        main.post {
            if (mode != Mode.BAR || closing) return@post
            removeSuggestions()
            val v = SideUi(this).suggestionLoading()
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = dp(10); y = barY }
            try { wm.addView(v, lp); sugLoadingView = v } catch (_: Exception) { return@post }
            v.translationX = dp(14).toFloat(); v.alpha = 0f
            v.animate().translationX(0f).alpha(1f).setDuration(180).start()
        }
    }

    /** Compact reply suggestions float beside the sidebar, directly over the active app. */
    fun showSuggestions(list: List<String>, title: String = "Förslag på svar") {
        if (mode != Mode.BAR || closing) return
        removeSuggestions()
        val v = SideUi(this).suggestions(list, title, { pickSuggestion(it) }, { removeSuggestions() })
        val lp = WindowManager.LayoutParams(
            min(dp(270), (screenW * 0.82f).toInt()), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = dp(10); y = barY }
        try { wm.addView(v, lp) } catch (_: Exception) { return }
        sugView = v
        v.translationX = dp(18).toFloat(); v.alpha = 0f
        v.animate().translationX(0f).alpha(1f).setDuration(180).start()
        main.postDelayed({ if (sugView === v) removeSuggestions() }, 30_000)
    }

    private fun pickSuggestion(text: String) {
        removeSuggestions()
        smart.markAccepted(text)
        pool.execute {
            val r = typeText(text, false)
            if (!r.startsWith("Skrev")) main.post { android.widget.Toast.makeText(this, "Tryck i textfältet först", android.widget.Toast.LENGTH_SHORT).show() }
        }
    }

    private fun hideDot() {
        dot?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        dot = null
    }

    // ----------------------------------------------------------- quick answer

    private fun onDotTap(px: Int = dotLp?.x ?: 0, py: Int = dotLp?.y ?: 0) {
        if (dotBusy) return
        val key = apiKey()
        val x = px
        val y = py
        dotBusy = true
        val id = ++requestId
        if (key.isEmpty()) {
            saveError("Ingen API-nyckel. Skriv in den i appen.")
            showAnswer("!", x, y)
            return
        }
        pool.execute {
            try {
                val (text, shot) = captureScreen()
                if (text.isBlank() && shot == null) throw RuntimeException("Kunde inte läsa skärmen (skyddad skärm?)")
                val answer = OpenAiClient(key).askOnce(text, shot)
                main.post { if (id == requestId && !closing) showAnswer(answer, x, y) }
            } catch (e: Exception) {
                saveError(e.message ?: "Okänt fel")
                main.post { if (id == requestId && !closing) showAnswer("!", x, y) }
            }
        }
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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
        main.postDelayed(answerTimeout, min(ANSWER_MAX_MS, ANSWER_MS + (lines - 1) * 1500L))
    }

    private fun removeAnswer() {
        answerView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        answerView = null
    }

    private fun finishAnswer() {
        main.removeCallbacks(answerTimeout)
        removeAnswer()
        dotBusy = false
        if (mode == Mode.HIDE && !closing) showDot()
    }

    private fun saveError(msg: String) {
        getSharedPreferences("prefs", MODE_PRIVATE).edit().putString("last_error", msg).apply()
    }

    // ----------------------------------------------------------- read screen

    /** The topmost real app window (not our own overlays). */
    private fun appRoot(): AccessibilityNodeInfo? {
        try {
            val ws = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .sortedByDescending { it.layer }
            for (w in ws) {
                val r = w.root ?: continue
                if (r.packageName?.toString() != packageName) return r
            }
        } catch (_: Exception) {
        }
        val r = rootInActiveWindow
        return if (r != null && r.packageName?.toString() != packageName) r else null
    }

    fun currentPkg(): String = appRoot()?.packageName?.toString() ?: lastPkg

    fun readScreenText(skipEditable: Boolean = false): String {
        val root = appRoot() ?: return ""
        val sb = StringBuilder()
        var count = 0
        var last = ""
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || count > 1500 || sb.length > 8000) return
            count++
            if (n.isVisibleToUser && !(skipEditable && n.isEditable)) {
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

    /** Screen text and a silent screenshot, with the panel made transparent while capturing. */
    fun captureScreen(): Pair<String, String?> {
        val v = panel?.root
        if (v != null) {
            main.post { v.alpha = 0f }
            Thread.sleep(120)
        }
        try {
            val text = readScreenText()
            val shot = takeShot()
            return Pair(text, shot)
        } finally {
            if (v != null) main.post { v.alpha = 1f }
        }
    }

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
        val scale = min(1f, 1600f / max(bmp.width, bmp.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        } else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    // --------------------------------------------------------------- actions

    private fun launch(i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        true
    } catch (e: Exception) {
        false
    }

    fun openApp(name: String): String {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(q, 0)
        val n = name.lowercase().trim()
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == n }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(n) }
            ?: apps.firstOrNull { val l = it.loadLabel(pm).toString().lowercase(); l.length > 3 && n.contains(l) }
            ?: return "Hittade ingen app som heter \"$name\"."
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return "Kunde inte starta appen."
        return if (launch(i)) "Öppnade ${hit.loadLabel(pm)}." else "Kunde inte öppna appen."
    }

    fun openUrl(url: String): String {
        val u = if (url.startsWith("http")) url else "https://$url"
        return if (launch(Intent(Intent.ACTION_VIEW, Uri.parse(u)))) "Öppnade $u." else "Kunde inte öppna adressen."
    }

    fun composeSms(number: String, message: String): String {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).putExtra("sms_body", message)
        return if (launch(i)) "SMS-utkast öppnat. Användaren trycker själv på skicka." else "Kunde inte öppna SMS."
    }

    fun composeEmail(to: String, subject: String, body: String): String {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            if (to.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return if (launch(i)) "Mejlutkast öppnat. Användaren trycker själv på skicka." else "Kunde inte öppna mejl."
    }

    fun dial(number: String): String =
        if (launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")))) "Öppnade uppringaren." else "Kunde inte öppna uppringaren."

    fun setAlarm(hour: Int, minute: Int, label: String): String {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour).putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (launch(i)) "Alarm ställt på %02d:%02d.".format(hour, minute) else "Kunde inte ställa alarm."
    }

    fun setTimer(seconds: Int, label: String): String {
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds).putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (launch(i)) "Timer startad på $seconds sekunder." else "Kunde inte starta timer."
    }

    private fun clickableAncestor(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var c = n
        var depth = 0
        while (c != null && depth < 8) {
            if (c.isClickable && c.isEnabled) return c
            c = c.parent
            depth++
        }
        return null
    }

    fun tapText(text: String): String {
        val root = appRoot() ?: return "Ingen app-skärm att trycka i."
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (nodes.isNullOrEmpty()) return "Hittade inget element med texten \"$text\"."
        val node = nodes.firstOrNull { it.isVisibleToUser } ?: nodes[0]
        val target = clickableAncestor(node) ?: node
        val r = android.graphics.Rect()
        target.getBoundsInScreen(r)
        ripple(r.exactCenterX(), r.exactCenterY())
        return if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) "Tryckte på \"$text\"." else "Kunde inte trycka på \"$text\"."
    }

    /** Short expanding ring at a point so the user can see where the assistant touches. */
    private fun ripple(x: Float, y: Float) {
        main.post {
            try {
                val size = dp(56)
                val v = android.view.View(this).apply {
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(0x557C5CFF)
                        setStroke(dp(2), 0xFFB9A8FF.toInt())
                    }
                    alpha = 0.9f
                    scaleX = 0.4f
                    scaleY = 0.4f
                }
                val lp = WindowManager.LayoutParams(
                    size, size,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    this.x = (x - size / 2).toInt()
                    this.y = (y - size / 2).toInt()
                }
                wm.addView(v, lp)
                v.animate().scaleX(1f).scaleY(1f).alpha(0f).setDuration(520).withEndAction {
                    try { wm.removeView(v) } catch (_: Exception) {}
                }.start()
            } catch (_: Exception) {}
        }
    }

    fun tapFraction(fx: Float, fy: Float): Boolean {
        val x = fx.coerceIn(0f, 1f) * screenW
        val y = fy.coerceIn(0f, 1f) * screenH
        ripple(x, y)
        val path = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build()
        return dispatchGesture(g, null, null)
    }

    /** Swipe / drag between two points (fractions of the screen). */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val ax = x1.coerceIn(0f, 1f) * screenW
        val ay = y1.coerceIn(0f, 1f) * screenH
        val bx = x2.coerceIn(0f, 1f) * screenW
        val by = y2.coerceIn(0f, 1f) * screenH
        ripple(ax, ay)
        val path = Path().apply { moveTo(ax, ay); lineTo(bx, by) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 450)).build()
        return dispatchGesture(g, null, null)
    }

    private fun findEditable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isEditable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) {
            val r = findEditable(n.getChild(i))
            if (r != null) return r
        }
        return null
    }

    private fun inputTarget(): AccessibilityNodeInfo? {
        val root = appRoot() ?: return null
        val f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        return if (f != null && f.isEditable) f else findEditable(root)
    }

    fun typeText(text: String, append: Boolean): String {
        val t = inputTarget() ?: return "Hittade inget textfält att skriva i."
        val cur = if (append && !t.isShowingHintText) t.text?.toString().orEmpty() else ""
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, cur + text)
        }
        return if (t.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "Skrev texten." else "Kunde inte skriva i fältet."
    }

    fun pressEnter(): String {
        val t = inputTarget() ?: return "Hittade inget textfält."
        return if (t.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) "Tryckte Enter." else "Kunde inte trycka Enter."
    }

    /** Tries the usual send buttons. */
    fun tapSend(): String {
        for (label in listOf("Skicka", "Send", "Skicka meddelande")) {
            val r = tapText(label)
            if (r.startsWith("Tryckte")) return r
        }
        return "Hittade ingen skicka-knapp. Tryck själv på skicka."
    }

    private fun findScrollable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isScrollable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) {
            val r = findScrollable(n.getChild(i))
            if (r != null) return r
        }
        return null
    }

    fun scroll(direction: String): String {
        val s = findScrollable(appRoot()) ?: return "Inget går att scrolla här."
        val act = if (direction.lowercase().startsWith("u")) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        return if (s.performAction(act)) "Scrollade." else "Kunde inte scrolla."
    }

    fun globalAct(action: String): String {
        val a = when (action.lowercase()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            else -> return "Okänd systemåtgärd: $action"
        }
        return if (performGlobalAction(a)) "Utförde $action." else "Kunde inte utföra $action."
    }

    companion object {
        const val ANSWER_MS = 5000L
        const val ANSWER_MAX_MS = 10000L
        const val DOUBLE_TAP_MS = 350L

        @Volatile
        var instance: ReaderService? = null
    }
}
