package com.example.screenbubble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Foreground service that shows a draggable round bubble over all apps.
 * Tap the bubble to open a chat panel. With "Se skärmen" on, a screenshot
 * is captured (via MediaProjection) and sent along with the question.
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val captureThread = HandlerThread("capture").apply { start() }
    private val captureHandler by lazy { Handler(captureThread.looper) }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var client: OpenAiClient? = null

    private var bubble: View? = null
    private var panel: View? = null
    private var card: View? = null
    private var chatText: TextView? = null
    private var chatScroll: ScrollView? = null
    private var input: EditText? = null
    private var screenSwitch: Switch? = null

    private val log = StringBuilder()
    private var screenW = 0
    private var screenH = 0
    private var dpi = 0

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (bubble != null) return START_NOT_STICKY

        startForegroundWithNotification()

        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val key = Config.embeddedKey().ifEmpty {
            getSharedPreferences("prefs", MODE_PRIVATE).getString("api_key", "").orEmpty()
        }
        if (key.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        client = OpenAiClient(key)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val bounds = wm.currentWindowMetrics.bounds
        screenW = bounds.width()
        screenH = bounds.height()
        dpi = resources.configuration.densityDpi

        val mpm = getSystemService(MediaProjectionManager::class.java)
        projection = mpm.getMediaProjection(code, data)
        // Android 14+ requires a callback to be registered before creating a display.
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                main.post { stopSelf() }
            }
        }, main)
        display = projection?.createVirtualDisplay(
            "screenbubble", screenW, screenH, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            null, null, null
        )

        showBubble()
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("bubble", "ScreenBubble", NotificationManager.IMPORTANCE_LOW)
        )
        val notification: Notification = Notification.Builder(this, "bubble")
            .setContentTitle("ScreenBubble är aktiv")
            .setContentText("Tryck för chatt · håll in för snabbsvar")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------- bubble

    private fun showBubble() {
        val size = dp(56)
        val v = TextView(this).apply {
            text = "AI"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF6C47FF.toInt())
            }
            elevation = dp(6).toFloat()
        }
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(200)
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        var longFired = false
        val longPress = Runnable {
            if (!moved) {
                longFired = true
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                quickAnswer()
            }
        }
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = e.rawX; touchY = e.rawY
                    moved = false
                    longFired = false
                    main.postDelayed(longPress, 500)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) {
                        moved = true
                        main.removeCallbacks(longPress)
                    }
                    lp.x = startX + dx
                    lp.y = startY + dy
                    wm.updateViewLayout(v, lp)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(longPress)
                    if (e.action == MotionEvent.ACTION_UP && !moved && !longFired) togglePanel()
                    true
                }
                else -> false
            }
        }
        wm.addView(v, lp)
        bubble = v
    }

    // ----------------------------------------------------------------- panel

    private fun togglePanel() {
        if (panel != null) {
            wm.removeView(panel)
            panel = null
            return
        }
        val p = buildPanel()
        val lp = WindowManager.LayoutParams(
            (screenW * 0.92f).toInt(),
            (screenH * 0.55f).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(24)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        wm.addView(p, lp)
        panel = p
        // Keep the bubble on top of the panel.
        bubble?.let {
            val blp = it.layoutParams
            wm.removeView(it)
            wm.addView(it, blp)
        }
    }

    private fun buildPanel(): View {
        val pad = dp(12)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(0xF5FFFFFF.toInt())
                cornerRadius = dp(20).toFloat()
            }
            elevation = dp(8).toFloat()
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        header.addView(
            TextView(this).apply {
                text = "ScreenBubble"
                textSize = 16f
                setTextColor(Color.BLACK)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(Button(this).apply {
            text = "Dölj"
            setOnClickListener { togglePanel() }
        })
        header.addView(Button(this).apply {
            text = "Avsluta"
            setOnClickListener { stopSelf() }
        })
        root.addView(header)

        val text = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.BLACK)
            setTextIsSelectable(true)
            this.text = log.toString()
        }
        val scroll = ScrollView(this).apply { addView(text) }
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        chatText = text
        chatScroll = scroll

        val sw = Switch(this).apply {
            this.text = "Se skärmen"
            isChecked = true
        }
        root.addView(sw)
        screenSwitch = sw

        root.addView(Button(this).apply {
            text = "Svara på frågorna på skärmen"
            setOnClickListener { quickAnswer() }
        })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val et = EditText(this).apply {
            hint = "Fråga något om skärmen..."
            maxLines = 3
        }
        row.addView(et, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            this.text = "Skicka"
            setOnClickListener { send() }
        })
        root.addView(row)
        input = et

        return root
    }

    private fun append(s: String) {
        log.append(s.replace("**", ""))
        chatText?.text = log.toString()
        chatScroll?.post { chatScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    // ------------------------------------------------------------------ send

    private fun send() {
        val q = input?.text?.toString()?.trim().orEmpty()
        if (q.isEmpty()) return
        val c = client ?: return
        val useScreen = screenSwitch?.isChecked == true
        input?.setText("")
        append("Du: $q\n\n")

        if (useScreen) {
            // Hide our own UI so it doesn't end up in the screenshot.
            panel?.visibility = View.INVISIBLE
            bubble?.visibility = View.INVISIBLE
        }

        worker.execute {
            try {
                var b64: String? = null
                if (useScreen) {
                    Thread.sleep(400)
                    b64 = captureBase64()
                    main.post {
                        panel?.visibility = View.VISIBLE
                        bubble?.visibility = View.VISIBLE
                    }
                }
                val answer = c.ask(q, b64)
                main.post { append("AI: $answer\n\n") }
            } catch (e: Exception) {
                main.post {
                    panel?.visibility = View.VISIBLE
                    bubble?.visibility = View.VISIBLE
                    append("Fel: ${e.message}\n\n")
                }
            }
        }
    }

    // --------------------------------------------------------------- capture

    /** Grabs one frame from the shared virtual display and returns it as base64 JPEG. */
    private fun captureBase64(): String? {
        val vd = display ?: return null
        // Handle rotation / size changes.
        val b = wm.currentWindowMetrics.bounds
        if (b.width() != screenW || b.height() != screenH) {
            screenW = b.width()
            screenH = b.height()
            vd.resize(screenW, screenH, dpi)
        }
        val reader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2)
        val latch = CountDownLatch(1)
        var result: Bitmap? = null

        reader.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                if (result == null) result = imageToBitmap(img)
            } finally {
                img.close()
            }
            latch.countDown()
        }, captureHandler)

        vd.surface = reader.surface
        latch.await(3, TimeUnit.SECONDS)
        vd.surface = null
        reader.close()

        val bmp = result ?: return null
        val scale = min(1f, 1280f / max(bmp.width, bmp.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        } else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun imageToBitmap(img: Image): Bitmap {
        val plane = img.planes[0]
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * img.width
        val padded = Bitmap.createBitmap(
            img.width + rowPadding / pixelStride, img.height, Bitmap.Config.ARGB_8888
        )
        padded.copyPixelsFromBuffer(plane.buffer)
        return Bitmap.createBitmap(padded, 0, 0, img.width, img.height)
    }

    // ----------------------------------------------------------- quick answer

    /** Reads questions/quizzes on screen and shows only the answers in a small card. */
    private fun quickAnswer() {
        val c = client ?: return
        dismissCard()
        panel?.visibility = View.INVISIBLE
        bubble?.visibility = View.INVISIBLE
        worker.execute {
            try {
                Thread.sleep(350)
                val b64 = captureBase64()
                main.post {
                    panel?.visibility = View.VISIBLE
                    bubble?.visibility = View.VISIBLE
                    (bubble as? TextView)?.text = "…"
                }
                val answer = c.askOnce(QUICK_PROMPT, b64)
                main.post {
                    (bubble as? TextView)?.text = "AI"
                    showAnswerCard(answer)
                }
            } catch (e: Exception) {
                main.post {
                    panel?.visibility = View.VISIBLE
                    bubble?.visibility = View.VISIBLE
                    (bubble as? TextView)?.text = "AI"
                    showAnswerCard("Fel: ${e.message}")
                }
            }
        }
    }

    private val cardTimeout = Runnable { dismissCard() }

    private fun showAnswerCard(text: String) {
        dismissCard()
        val tv = TextView(this).apply {
            this.text = text.replace("**", "")
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(0xCC202020.toInt())
                cornerRadius = dp(14).toFloat()
            }
            setOnClickListener { dismissCard() }
        }
        val lp = WindowManager.LayoutParams(
            (screenW * 0.8f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(48)
        }
        wm.addView(tv, lp)
        card = tv
        main.postDelayed(cardTimeout, 20_000)
    }

    private fun dismissCard() {
        main.removeCallbacks(cardTimeout)
        card?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        card = null
    }

    // --------------------------------------------------------------- cleanup

    override fun onDestroy() {
        dismissCard()
        try { panel?.let { wm.removeView(it) } } catch (_: Exception) {}
        try { bubble?.let { wm.removeView(it) } } catch (_: Exception) {}
        panel = null
        bubble = null
        display?.release()
        projection?.stop()
        worker.shutdownNow()
        captureThread.quitSafely()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CODE = "result_code"
        const val EXTRA_DATA = "result_data"
        private const val QUICK_PROMPT =
            "Svara på frågorna eller quizet som syns på skärmen. Ge bara rätt svar."
    }
}
