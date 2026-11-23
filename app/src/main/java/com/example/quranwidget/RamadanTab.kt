package com.example.quranwidget

import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import java.io.File

class RamadanTab(a: MainActivity) : Tab(a) {
    override fun build(): View {
        val box = act.vbox()
        box.put(act.tv("رمضان", 20f, "#0B3D2E", true))

        box.put(Switch(act).apply {
            text = "مدفع الإفطار عند أذان المغرب (في رمضان فقط)"
            isChecked = P.cannonOn(act)
            setOnCheckedChangeListener { _, v -> P.setCannonOn(act, v) }
        }, 12)
        val cTv = act.tv("الصوت: ${P3.cannonName(act) ?: "صوت المدفع الافتراضي"}", 14f, "#555555")
        box.put(cTv, 4)
        box.put(act.btn("اختيار صوت المدفع (ملف من جهازك أو رابط)") {
            Pickers.audio(act, "cannon") { f: File, n: String ->
                P3.setCannon(act, f.absolutePath, n); cTv.text = "الصوت: $n"
            }
        }, 4)
        box.put(act.btn("استرجاع صوت المدفع الافتراضي") {
            P3.clearCannon(act); cTv.text = "الصوت: صوت المدفع الافتراضي"
        }, 4)
        box.put(act.btn("▶ تجربة المدفع") { AthanService.start(act, AthanService.KIND_CANNON) }, 4)

        box.put(Switch(act).apply {
            text = "المسحراتي (في رمضان فقط)"
            isChecked = P.suhoorOn(act)
            setOnCheckedChangeListener { _, v -> P.setSuhoorOn(act, v); Scheduler.scheduleAll(act) }
        }, 20)
        val mTv = act.tv("يبدأ قبل الفجر بـ ${P.suhoorMin(act)} دقيقة", 15f)
        val row = act.hbox()
        row.addView(mTv, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(act.btn("−") { P.setSuhoorMin(act, P.suhoorMin(act) - 5); mTv.text = "يبدأ قبل الفجر بـ ${P.suhoorMin(act)} دقيقة"; Scheduler.scheduleAll(act) })
        row.addView(act.btn("+") { P.setSuhoorMin(act, P.suhoorMin(act) + 5); mTv.text = "يبدأ قبل الفجر بـ ${P.suhoorMin(act)} دقيقة"; Scheduler.scheduleAll(act) })
        box.put(row)
        val sTv = act.tv("الصوت: ${P.suhoorName(act) ?: "صوت مسحراتي بالنطق الآلي"}", 14f, "#555555")
        box.put(sTv)
        box.put(act.btn("اختيار صوت مسحراتي (رابط أو ملف)") {
            Pickers.audio(act, "pre") { f: File, n: String ->
                P.setSuhoor(act, f.absolutePath, n); sTv.text = "الصوت: $n"
            }
        }, 4)
        box.put(act.btn("▶ تجربة المسحراتي") { AthanService.start(act, AthanService.KIND_SUHOOR) }, 4)

        box.put(act.tv("تعديل التاريخ الهجري", 17f, "#0B3D2E", true), 24)
        val oTv = act.tv(offText(), 15f)
        val oRow = act.hbox()
        oRow.addView(oTv, LinearLayout.LayoutParams(0, -2, 1f))
        oRow.addView(act.btn("−") { P.setHijriOffset(act, P.hijriOffset(act) - 1); oTv.text = offText(); Scheduler.scheduleAll(act) })
        oRow.addView(act.btn("+") { P.setHijriOffset(act, P.hijriOffset(act) + 1); oTv.text = offText(); Scheduler.scheduleAll(act) })
        box.put(oRow)
        box.put(act.tv("اليوم: ${Dates.line(act, java.time.LocalDate.now()).substringBefore("\n")}", 14f, "#555555"), 4)
        box.put(act.tv("لو بداية الشهر في بلدك تختلف عن التقويم، عدّل بيوم زيادة أو نقص.", 12f, "#777777"), 4)

        box.put(act.btn("⏹ إيقاف الصوت") { AthanService.stop(act) }, 20)
        return box
    }

    private fun offText(): String {
        val o = P.hijriOffset(act)
        return "فرق الأيام: " + (if (o > 0) "+$o" else "$o")
    }
}
