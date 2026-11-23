package com.example.quranwidget

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate

class PrayerTab(a: MainActivity) : Tab(a) {
    override val cardWrap: Boolean get() = false

    private val h = Handler(Looper.getMainLooper())
    private var nameTv: TextView? = null
    private var countTv: TextView? = null
    private var atTv: TextView? = null
    private val rows = ArrayList<LinearLayout>()
    private var lastIdx = -2
    private val tick = object : Runnable {
        override fun run() { update(); h.postDelayed(this, 1000) }
    }

    private val cities = listOf(
        Triple("القاهرة", 30.0444, 31.2357), Triple("الإسكندرية", 31.2001, 29.9187),
        Triple("الجيزة", 30.0131, 31.2089), Triple("بلبيس - الشرقية", 30.4180, 31.5610),
        Triple("الزقازيق", 30.5877, 31.5020), Triple("المنصورة", 31.0409, 31.3785),
        Triple("طنطا", 30.7865, 31.0004), Triple("الإسماعيلية", 30.5965, 32.2715),
        Triple("بورسعيد", 31.2653, 32.3019), Triple("السويس", 29.9668, 32.5498),
        Triple("أسيوط", 27.1809, 31.1837), Triple("الأقصر", 25.6872, 32.6396),
        Triple("أسوان", 24.0889, 32.8998), Triple("شرم الشيخ", 27.9158, 34.3300),
        Triple("مكة المكرمة", 21.3891, 39.8579), Triple("المدينة المنورة", 24.5247, 39.5692)
    )

    override fun build(): View {
        val box = act.vbox(0)
        val today = LocalDate.now()

        // ---------- البطاقة الرئيسية ----------
        val hero = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = Art.hero(act)
            setPadding(act.dp(20), act.dp(20), act.dp(20), act.dp(22))
            elevation = act.dp(4).toFloat()
            round(24)
        }
        val chipRow = act.hbox()
        val place = act.tv("📍 ${P.place(act)}", 13f, "#FFFFFF").apply {
            background = Art.chip(act)
            setPadding(act.dp(12), act.dp(5), act.dp(12), act.dp(5))
        }
        chipRow.addView(place)
        hero.addView(chipRow)
        hero.put(act.tv(Dates.line(act, today), 15f, "#F1DE9A").apply { setLineSpacing(0f, 1.2f) }, 10)

        hero.put(act.tv("الصلاة القادمة", 14f, "#CFE8D5").apply { gravity = Gravity.CENTER }, 14)
        nameTv = act.tv("", 38f, "#FFFFFF", true).apply { gravity = Gravity.CENTER }
        hero.put(nameTv!!, 0)
        countTv = act.tv("", 30f, "#F1DE9A", true).apply { gravity = Gravity.CENTER; typeface = android.graphics.Typeface.MONOSPACE }
        hero.put(countTv!!, 0)
        atTv = act.tv("", 14f, "#CFE8D5").apply { gravity = Gravity.CENTER }
        hero.put(atTv!!, 2)
        box.put(hero, 0)

        // ---------- جدول الصلوات ----------
        val list = act.card(10)
        val t = Times.day(act, today)
        val names = listOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")
        val icons = listOf("🌙", "🌅", "☀️", "🌤", "🌇", "🌌")
        rows.clear()
        for (i in 0..5) {
            val row = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(act.dp(12), act.dp(11), act.dp(12), act.dp(11))
            }
            row.addView(act.tv(icons[i], 22f).apply { setPadding(0, 0, act.dp(12), 0) })
            row.addView(act.tv(names[i], 19f, "#1F2A24", i != 1), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(act.tv(Dates.fmt(t[i]), 19f, "#0B3D2E", true))
            list.put(row, 2)
            rows.add(row)
        }
        box.put(list, 14)

        // ---------- الضبط ----------
        val set = act.card(14)
        set.put(act.tv("ضبط الموقع والتنبيهات", 20f, "#0B3D2E", true), 0)
        set.put(act.btn("📍 تحديد موقعي تلقائياً") {
            act.locate { ok -> if (ok) act.show(0) else act.toast("تعذر تحديد الموقع، اختر مدينتك يدوياً") }
        }, 10)
        set.put(act.btn("🏙️ اختيار المدينة يدوياً") {
            AlertDialog.Builder(act).setTitle("اختر المدينة")
                .setItems(cities.map { it.first }.toTypedArray()) { _, i ->
                    val c = cities[i]
                    P.setPlace(act, c.first, c.second, c.third)
                    Scheduler.scheduleAll(act)
                    act.show(0)
                }.show()
        }, 6)
        set.put(act.tv("لضمان عمل الأذان في وقته:", 14f, "#6B7A70", true), 14)
        set.put(act.btn("1) السماح بالتنبيهات الدقيقة") {
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    act.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${act.packageName}"))
                    )
                } catch (e: Exception) { act.toast("مفعّل بالفعل") }
            } else act.toast("مفعّل بالفعل")
        }, 4)
        set.put(act.btn("2) السماح بالإشعارات") { act.askNotifications() }, 6)
        set.put(act.btn("3) السماح بالعمل في الخلفية (إيقاف توفير البطارية)") {
            act.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }, 6)
        set.put(act.btn("🔄 إعادة جدولة المنبهات") {
            Scheduler.scheduleAll(act); act.toast("تمت إعادة الجدولة")
        }, 6)
        box.put(set, 14)

        lastIdx = -2
        return box
    }

    private fun next(): Triple<Int, String, Long> {
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        for (d in 0..1) {
            val t = Times.day(act, today.plusDays(d.toLong()))
            for (p in 0..4) {
                val ms = t[Times.IDX[p]]
                if (ms > now) return Triple(if (d == 0) p else -1, P.prayerNames[p], ms)
            }
        }
        return Triple(-1, "", now)
    }

    private fun highlight(p: Int) {
        for ((i, r) in rows.withIndex()) {
            val on = p >= 0 && i == Times.IDX[p]
            r.background = if (on) android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(70, 201, 162, 39)); cornerRadius = act.dp(14).toFloat()
            } else null
        }
    }

    private fun update() {
        val (p, name, ms) = next()
        if (p != lastIdx) { lastIdx = p; highlight(p) }
        val s = ((ms - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        nameTv?.text = name
        countTv?.text = String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
        atTv?.text = "عند ${Dates.fmt(ms)}"
    }

    override fun onShow() { h.removeCallbacks(tick); h.post(tick) }
    override fun onHide() { h.removeCallbacks(tick) }
}
