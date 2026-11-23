package com.example.quranwidget

import android.graphics.Color
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import java.time.LocalDate
import java.time.YearMonth
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoUnit

class CalendarTab(a: MainActivity) : Tab(a) {
    private var hijriMode = true
    private var ym = YearMonth.now()
    private var hy = Dates.hYear(a, LocalDate.now())
    private var hm = Dates.hMonth(a, LocalDate.now())
    private var holder: LinearLayout? = null

    override fun build(): View {
        val box = act.vbox(10)
        box.put(act.tv("الإمساكية والتقويم", 20f, "#0B3D2E", true))
        val top = act.hbox()
        top.addView(act.btn("هجري") { hijriMode = true; render() }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(act.btn("ميلادي") { hijriMode = false; render() }, LinearLayout.LayoutParams(0, -2, 1f))
        box.put(top)
        holder = act.vbox(0)
        box.put(holder!!)
        render()
        return box
    }

    private fun step(d: Int) {
        if (hijriMode) {
            hm += d
            if (hm > 12) { hm = 1; hy++ }
            if (hm < 1) { hm = 12; hy-- }
        } else ym = ym.plusMonths(d.toLong())
        render()
    }

    private fun days(): List<LocalDate> {
        val off = P.hijriOffset(act).toLong()
        return if (hijriMode) {
            val first = HijrahDate.of(hy, hm, 1)
            val len = first.lengthOfMonth()
            (0 until len).map { LocalDate.from(first.plus(it.toLong(), ChronoUnit.DAYS)).minusDays(off) }
        } else (1..ym.lengthOfMonth()).map { ym.atDay(it) }
    }

    private fun render() {
        val h = holder ?: return
        h.removeAllViews()
        val title = if (hijriMode) "${Dates.hMonths[hm - 1]} $hy هـ" else "${Dates.gMonths[ym.monthValue - 1]} ${ym.year}"
        val nav = act.hbox()
        nav.addView(act.btn("‹") { step(-1) })
        nav.addView(act.tv(title, 18f, "#0B3D2E", true).apply { gravity = android.view.Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(act.btn("›") { step(1) })
        h.put(nav)

        val ds = days()
        val ramadan = hijriMode && hm == 9
        val heads = mutableListOf("اليوم", "م", "هـ")
        if (ramadan) heads.add("إمساك")
        heads.addAll(listOf("فجر", "شروق", "ظهر", "عصر", "مغرب", "عشاء"))

        val table = TableLayout(act)
        table.layoutDirection = View.LAYOUT_DIRECTION_LTR
        table.addView(row(heads, true, false))
        val today = LocalDate.now()
        for (d in ds) {
            val t = Times.day(act, d)
            val cells = mutableListOf(Dates.dayName(d), "${d.dayOfMonth}/${d.monthValue}", "${Dates.hDay(act, d)}/${Dates.hMonth(act, d)}")
            if (ramadan) cells.add(Dates.fmt(t[0] - 10 * 60_000L, false))
            for (i in 0..5) cells.add(Dates.fmt(t[i], false))
            table.addView(row(cells, false, d == today))
        }
        val hs = HorizontalScrollView(act)
        hs.addView(table)
        h.put(hs, 8)
        if (ramadan) h.put(act.tv("الإمساك = قبل الفجر بعشر دقائق. تقويم الهجري قد يختلف يوماً حسب الرؤية، وتقدر تعدله من تبويب رمضان.", 12f, "#777777"), 8)
    }

    private fun row(cells: List<String>, header: Boolean, today: Boolean): TableRow {
        val r = TableRow(act)
        if (header) r.setBackgroundColor(Color.parseColor("#0B3D2E"))
        else if (today) r.setBackgroundColor(Color.parseColor("#FFE9A8"))
        for (c in cells) {
            val t = TextView(act)
            t.text = c
            t.textSize = 12f
            t.setPadding(act.dp(6), act.dp(5), act.dp(6), act.dp(5))
            t.setTextColor(if (header) Color.WHITE else Color.parseColor("#1B1B1B"))
            r.addView(t)
        }
        return r
    }
}
