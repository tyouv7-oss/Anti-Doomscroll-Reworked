package com.mini.scroll

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView

class ScrollService : AccessibilityService() {

    private val step = 50 // layar peringatan muncul tiap kelipatan 50 reels
    private val h = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var pill: LinearLayout? = null
    private var num: TextView? = null
    private var gate: View? = null
    private var last = 0L
    private val wUpd = Runnable { BrainWidget.refresh(this) }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { hidePill(); closeGate() }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun sb(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun lp(wd: Int, ht: Int, touch: Boolean) = WindowManager.LayoutParams(
        wd, ht,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (touch) WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT
    )

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        val ev = e ?: return
        val p = ev.packageName?.toString() ?: return
        if (ev.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (p !in Apps.names) return
            showPill()
            val now = SystemClock.elapsedRealtime()
            val fresh = now - last > 400 // debounce: 1 swipe = 1 hitungan
            last = now
            if (fresh) {
                val n = Store.add(this, p)
                paint(n)
                h.removeCallbacks(wUpd)
                h.postDelayed(wUpd, 1500)
                if (n % step == 0) warn(n)
            }
        } else if (p in Apps.names) {
            showPill()
        } else if (p == packageName) {
            if (ev.className?.toString() == MainActivity::class.java.name) hidePill()
        } else if (listOf("systemui", "inputmethod", "keyboard").none { p.contains(it) }) {
            hidePill()
        }
    }

    private fun showPill() {
        if (pill != null) return
        val icon = TextView(this).apply { text = "🧠"; textSize = 14f }
        num = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), 0, 0, 0)
        }
        pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(5), dp(14), dp(5))
            addView(icon)
            addView(num)
        }
        val params = lp(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, false)
            .apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = sb() + dp(2) }
        runCatching { wm.addView(pill, params) }
        paint(Store.today(this))
    }

    private fun paint(n: Int) {
        val (fg, bg) = when {
            n >= 100 -> 0xFFFF5A5A.toInt() to 0xE63A1212.toInt()
            n >= 50 -> 0xFFFF9A3D.toInt() to 0xE6402414.toInt()
            else -> 0xFFFFC46B.toInt() to 0xE6301E18.toInt()
        }
        num?.apply { text = n.toString(); setTextColor(fg) }
        pill?.background = GradientDrawable().apply { cornerRadius = dp(40).toFloat(); setColor(bg) }
    }

    private fun hidePill() {
        pill?.let { v -> runCatching { wm.removeView(v) } }
        pill = null
        num = null
    }

    private fun warn(n: Int) {
        if (gate != null) return
        val wc = LinearLayout.LayoutParams.WRAP_CONTENT
        val mp = LinearLayout.LayoutParams.MATCH_PARENT
        fun box(w: Int, top: Int) = LinearLayout.LayoutParams(w, wc).apply { topMargin = dp(top) }
        fun btn(color: Int, r: Int) = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(color) }

        val title = TextView(this).apply {
            text = "Kemampuan fokusmu\nmenurun."
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            rotation = -6f
        }
        val count = TextView(this).apply {
            text = "$n reels"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFF3B3B.toInt())
            setPadding(dp(22), dp(10), dp(22), dp(10))
            background = btn(0xFF3A1010.toInt(), 40)
        }
        val brain = TextView(this).apply { text = "🧠"; textSize = 110f; gravity = Gravity.CENTER }
        val leave = TextView(this).apply {
            text = "Keluar dari aplikasi"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF2B1A0E.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(16), dp(24), dp(16))
            background = btn(0xFFE8B98A.toInt(), 30)
            setOnClickListener { closeGate(); performGlobalAction(GLOBAL_ACTION_HOME) }
        }
        val stay = TextView(this).apply {
            textSize = 13f
            setTextColor(0x99FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }

        gate = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(dp(32), dp(32), dp(32), dp(32))
            addView(title)
            addView(count, box(wc, 24))
            addView(brain, box(wc, 16))
            addView(leave, box(mp, 24))
            addView(stay, box(mp, 8))
        }
        runCatching {
            wm.addView(gate, lp(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, true))
        }

        var left = 5 // tombol "Lanjut scroll" aktif setelah 5 detik
        val tick = object : Runnable {
            override fun run() {
                if (gate == null) return
                if (left > 0) {
                    stay.text = "Lanjut scroll ($left)"
                    left--
                    h.postDelayed(this, 1000)
                } else {
                    stay.text = "Lanjut scroll"
                    stay.setOnClickListener { closeGate() }
                }
            }
        }
        h.post(tick)
    }

    private fun closeGate() {
        gate?.let { v -> runCatching { wm.removeView(v) } }
        gate = null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        hidePill()
        closeGate()
        runCatching { unregisterReceiver(screenOff) }
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}
}
