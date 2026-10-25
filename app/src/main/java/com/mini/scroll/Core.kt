package com.mini.scroll

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Apps {
    val names = mapOf(
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.facebook.katana" to "Facebook",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok",
        "com.twitter.android" to "X"
    )
}

object Store {
    fun key(d: Date): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)
    private fun sp(c: Context) = c.getSharedPreferences("bp", Context.MODE_PRIVATE)

    fun add(c: Context, pkg: String): Int {
        val s = sp(c)
        val k = "${key(Date())}|$pkg"
        s.edit().putInt(k, s.getInt(k, 0) + 1).apply()
        return today(c)
    }

    fun perApp(c: Context, day: String): Map<String, Int> =
        sp(c).all.filterKeys { it.startsWith("$day|") }.entries
            .associate { it.key.substringAfter('|') to ((it.value as? Int) ?: 0) }

    fun total(c: Context, day: String): Int = perApp(c, day).values.sum()
    fun today(c: Context): Int = total(c, key(Date()))
}

class BrainWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) = refresh(c, m, ids)

    companion object {
        fun refresh(
            c: Context,
            m: AppWidgetManager = AppWidgetManager.getInstance(c),
            ids: IntArray = m.getAppWidgetIds(ComponentName(c, BrainWidget::class.java))
        ) {
            if (ids.isEmpty()) return
            val v = RemoteViews(c.packageName, R.layout.widget)
            v.setTextViewText(R.id.w_count, Store.today(c).toString())
            v.setOnClickPendingIntent(
                R.id.w_root,
                PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            )
            m.updateAppWidget(ids, v)
        }
    }
}
