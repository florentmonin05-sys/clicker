package fr.perso.randomclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

class ClickService : AccessibilityService() {

    companion object {
        const val PREFS = "cfg"
        @Volatile
        var instance: ClickService? = null
    }

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var panel: View? = null
    private var marker: View? = null
    private var picker: View? = null
    private var status: TextView? = null
    private var startBtn: TextView? = null

    private var targetX = -1f
    private var targetY = -1f
    private var running = false
    private var clickCount = 0
    private var lastDelay = -1L

    private val clickRunnable = Runnable { doClick() }

    // ---------- Cycle de vie ----------

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showPanel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        running = false
        handler.removeCallbacks(clickRunnable)
        safeRemove(panel); panel = null
        safeRemove(marker); marker = null
        safeRemove(picker); picker = null
        instance = null
    }

    private fun safeRemove(v: View?) {
        if (v != null) try { wm.removeView(v) } catch (_: Exception) {}
    }

    // ---------- Clics ----------

    private fun startClicking() {
        if (targetX < 0) {
            setStatus("Choisis d'abord une position")
            return
        }
        running = true
        clickCount = 0
        startBtn?.text = "■ Arrêter"
        startBtn?.background = round(0xFFC62828.toInt(), dp(8))
        scheduleNext()
    }

    private fun stopClicking() {
        running = false
        handler.removeCallbacks(clickRunnable)
        startBtn?.text = "▶ Démarrer"
        startBtn?.background = round(0xFF2E7D32.toInt(), dp(8))
        setStatus("Arrêté ($clickCount clics)")
    }

    private fun scheduleNext() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val min = prefs.getInt("min", 10).coerceAtLeast(1)
        val max = prefs.getInt("max", 50).coerceAtLeast(min)
        val minMs = min * 1000L
        val maxMs = max * 1000L

        var delay: Long
        do {
            delay = Random.nextLong(minMs, maxMs + 1)
        } while (delay == lastDelay && maxMs > minMs)
        lastDelay = delay

        setStatus("Clics : $clickCount — prochain dans ${delay / 1000}s")
        handler.postDelayed(clickRunnable, delay)
    }

    private fun doClick() {
        if (!running) return
        // léger décalage aléatoire de ±3 px et durée d'appui variable
        val x = targetX + Random.nextInt(-3, 4)
        val y = targetY + Random.nextInt(-3, 4)
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, Random.nextLong(40, 90))
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        clickCount++
        scheduleNext()
    }

    // ---------- Panneau flottant ----------

    fun togglePanel() {
        if (panel == null) showPanel() else hidePanel()
    }

    private fun hidePanel() {
        safeRemove(panel); panel = null
    }

    private fun showPanel() {
        if (panel != null) return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = round(0xE6202020.toInt(), dp(12))
        }
        val handle = TextView(this).apply {
            text = "≡ Random Clicker (glisser)"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }
        status = TextView(this).apply {
            text = if (targetX < 0) "Choisis une position" else "Position prête"
            setTextColor(0xFFBDBDBD.toInt())
            textSize = 11f
            setPadding(0, 0, 0, dp(6))
        }
        val pick = button("📍 Choisir position", 0xFF3F51B5.toInt()) { startPicking() }
        startBtn = button("▶ Démarrer", 0xFF2E7D32.toInt()) {
            if (running) stopClicking() else startClicking()
        }
        val hide = button("✕ Masquer", 0xFF616161.toInt()) { hidePanel() }

        root.addView(handle)
        root.addView(status)
        root.addView(pick)
        root.addView(startBtn)
        root.addView(hide)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16); y = dp(200)
        }

        // glisser le panneau par la poignée
        var startX = 0; var startY = 0; var downX = 0f; var downY = 0f
        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y; downX = e.rawX; downY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - downX).toInt()
                    lp.y = startY + (e.rawY - downY).toInt()
                    try { wm.updateViewLayout(root, lp) } catch (_: Exception) {}
                }
            }
            true
        }

        wm.addView(root, lp)
        panel = root
    }

    // ---------- Choix de la position ----------

    private fun startPicking() {
        if (picker != null) return
        if (running) stopClicking()

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0x66000000)
            addView(TextView(context).apply {
                text = "Touche l'endroit où cliquer"
                setTextColor(Color.WHITE)
                textSize = 20f
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        overlay.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                targetX = e.rawX
                targetY = e.rawY
                safeRemove(picker); picker = null
                showMarker()
                setStatus("Position : ${targetX.toInt()}, ${targetY.toInt()}")
            }
            true
        }

        val lp = fullScreenParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )
        wm.addView(overlay, lp)
        picker = overlay
    }

    private fun showMarker() {
        safeRemove(marker)
        val size = dp(22)
        val dot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xAAFF1744.toInt())
                setStroke(dp(2), Color.WHITE)
            }
        }
        val lp = fullScreenParams(
            size, size,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        ).apply {
            x = (targetX - size / 2f).toInt()
            y = (targetY - size / 2f).toInt()
        }
        wm.addView(dot, lp)
        marker = dot
    }

    // ---------- Utilitaires ----------

    /** Fenêtre alignée sur les coordonnées réelles de l'écran (rawX/rawY). */
    private fun fullScreenParams(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    private fun setStatus(t: String) { status?.text = t }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun round(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius.toFloat()
    }

    private fun button(label: String, color: Int, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = round(color, dp(8))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) }
        setOnClickListener { onClick() }
    }
}
