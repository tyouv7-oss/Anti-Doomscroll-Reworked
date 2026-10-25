package com.mini.scroll

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.HashMap
import java.util.Locale

class MainActivity : Activity() {

    private var off = 0 // 0 = minggu ini, -1 = minggu lalu, dst.
    private lateinit var root: LinearLayout
    private val tan = 0xFFE8B98A.toInt()
    private val gray = 0xFF8A8A8A.toInt()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun tv(t: String, sp: Float, c: Int, bold: Boolean = false, g: Int = Gravity.START) =
        TextView(this).apply {
            text = t
            textSize = sp
            setTextColor(c)
            gravity = g
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun enabled(): Boolean =
        Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true

    private fun icon(label: String): Drawable? =
        Apps.names.filterValues { it == label }.keys
            .firstNotNullOfOrNull { runCatching { packageManager.getApplicationIcon(it) }.getOrNull() }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.BLACK); addView(root) })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        root.removeAllViews()
        val m = LinearLayout.LayoutParams.MATCH_PARENT
        val w = LinearLayout.LayoutParams.WRAP_CONTENT
        fun box(top: Int) = LinearLayout.LayoutParams(m, w).apply { topMargin = dp(top) }

        root.addView(tv("🧠  BRAINPAL", 13f, tan, true))

        if (!enabled()) {
            root.addView(
                tv("Aktifkan BrainPal", 15f, 0xFF2B1A0E.toInt(), true, Gravity.CENTER).apply {
                    setPadding(dp(24), dp(16), dp(24), dp(16))
                    background = GradientDrawable().apply { cornerRadius = dp(30).toFloat(); setColor(tan) }
                    setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                }, box(16)
            )
            root.addView(
                tv("Aksesibilitas → BrainPal → aktifkan. Android 13+: Info aplikasi → ⋮ → Izinkan setelan terbatas.", 12f, gray),
                box(8)
            )
        }

        // navigasi minggu
        val cal = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            add(Calendar.DAY_OF_YEAR, off * 7)
        }
        val days = (0..6).map { i -> (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }.time }
        val f = SimpleDateFormat("d MMM", Locale.getDefault())
        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        nav.addView(tv("‹", 26f, tan, true).apply {
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { off--; render() }
        })
        nav.addView(
            tv("${f.format(days[0])} – ${f.format(days[6])}", 14f, Color.WHITE, true, Gravity.CENTER),
            LinearLayout.LayoutParams(0, w, 1f)
        )
        nav.addView(tv("›", 26f, if (off < 0) tan else 0xFF444444.toInt(), true).apply {
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { if (off < 0) { off++; render() } }
        })
        root.addView(nav, box(24))

        // grafik batang
        val counts = days.map { Store.total(this, Store.key(it)) }
        val ti = days.indexOfFirst { Store.key(it) == Store.key(Date()) }
        root.addView(Bars(this, counts, ti), LinearLayout.LayoutParams(m, dp(190)).apply { topMargin = dp(20) })
        root.addView(tv("${counts.sum()}", 44f, Color.WHITE, true, Gravity.CENTER), box(24))
        root.addView(tv("Total reels", 12f, gray, false, Gravity.CENTER), box(0))

        // top apps
        val by = HashMap<String, Int>()
        days.forEach { d ->
            Store.perApp(this, Store.key(d)).forEach { (p, n) ->
                val l = Apps.names[p] ?: p
                by[l] = (by[l] ?: 0) + n
            }
        }
        root.addView(tv("Top apps", 12f, gray), box(28))
        Apps.names.values.distinct().sortedByDescending { by[it] ?: 0 }.forEach { l ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(ImageView(this).apply { icon(l)?.let { setImageDrawable(it) } }, LinearLayout.LayoutParams(dp(32), dp(32)))
            row.addView(tv(l, 15f, Color.WHITE).apply { setPadding(dp(14), 0, 0, 0) }, LinearLayout.LayoutParams(0, w, 1f))
            row.addView(tv("${by[l] ?: 0}", 15f, tan, true))
            root.addView(row, box(14))
        }
    }
}

class Bars(c: Context, private val v: List<Int>, private val todayIdx: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lab = "SSRKJSM"

    override fun onDraw(cv: Canvas) {
        val d = resources.displayMetrics.density
        val slot = width / 7f
        val top = 20 * d
        val bot = height - 22 * d
        val mx = maxOf(v.maxOrNull() ?: 0, 1)
        p.textAlign = Paint.Align.CENTER
        p.textSize = 11 * d
        for (i in 0..6) {
            val cx = slot * i + slot / 2
            val bh = maxOf((bot - top) * v[i] / mx, 4 * d)
            p.color = if (v[i] > 0) 0xFFE8B98A.toInt() else 0xFF2A2A2A.toInt()
            cv.drawRoundRect(cx - 9 * d, bot - bh, cx + 9 * d, bot, 9 * d, 9 * d, p)
            if (v[i] > 0) {
                p.color = Color.WHITE
                cv.drawText("${v[i]}", cx, bot - bh - 6 * d, p)
            }
            p.color = if (i == todayIdx) Color.WHITE else 0xFF777777.toInt()
            cv.drawText(lab[i].toString(), cx, height - 6 * d, p)
        }
    }
}
