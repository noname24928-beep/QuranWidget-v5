package com.example.quranwidget

import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Switch
import java.io.File

class SalawatTab(a: MainActivity) : Tab(a) {
    private val steps = listOf(1, 2, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240)

    override fun build(): View {
        val box = act.vbox()
        box.put(act.tv("الصلاة على النبي ﷺ", 20f, "#0B3D2E", true))

        box.put(Switch(act).apply {
            text = "تشغيل الصلاة على النبي تلقائياً"
            isChecked = P3.salOn(act)
            setOnCheckedChangeListener { _, v -> P3.put(act, "sal_on", v); Scheduler.scheduleAll(act) }
        }, 12)

        // الفاصل الزمني
        val iTv = act.tv("تتكرر كل ${P3.salMin(act)} دقيقة", 15f)
        val iRow = act.hbox()
        iRow.addView(iTv, LinearLayout.LayoutParams(0, -2, 1f))
        fun moveInterval(d: Int) {
            val cur = P3.salMin(act)
            var idx = steps.indexOfFirst { it >= cur }.let { if (it < 0) steps.size - 1 else it }
            idx = (idx + d).coerceIn(0, steps.size - 1)
            P3.putInt(act, "sal_min", steps[idx])
            iTv.text = "تتكرر كل ${steps[idx]} دقيقة"
            Scheduler.scheduleAll(act)
        }
        iRow.addView(act.btn("−") { moveInterval(-1) })
        iRow.addView(act.btn("+") { moveInterval(1) })
        box.put(iRow, 10)

        // ساعات العمل
        val hTv = act.tv(hoursText(), 15f)
        val hRow = act.hbox()
        hRow.addView(hTv, LinearLayout.LayoutParams(0, -2, 1f))
        hRow.addView(act.btn("بداية −") { P3.putInt(act, "sal_start", (P3.salStart(act) + 23) % 24); hTv.text = hoursText() })
        hRow.addView(act.btn("بداية +") { P3.putInt(act, "sal_start", (P3.salStart(act) + 1) % 24); hTv.text = hoursText() })
        box.put(hRow, 6)
        val h2 = act.hbox()
        h2.addView(act.btn("نهاية −") { P3.putInt(act, "sal_end", (P3.salEnd(act) + 23) % 24); hTv.text = hoursText() }, LinearLayout.LayoutParams(0, -2, 1f))
        h2.addView(act.btn("نهاية +") { P3.putInt(act, "sal_end", (P3.salEnd(act) + 1) % 24); hTv.text = hoursText() }, LinearLayout.LayoutParams(0, -2, 1f))
        box.put(h2, 2)
        box.put(act.tv("لا يعمل أثناء الأذان أو تلاوة المصحف، وإنما ينتظر الموعد التالي.", 12f, "#777777"), 4)

        // الصيغ
        box.put(act.tv("الصيغ (اختر واحدة أو أكثر، وتُختار عشوائياً)", 16f, "#0B3D2E", true), 18)
        val chosen = P3.salFormulas(act).toMutableSet()
        SalawatService.formulas.forEachIndexed { i, f ->
            box.put(CheckBox(act).apply {
                text = f
                textSize = 14f
                isChecked = i in chosen
                setOnCheckedChangeListener { _, v ->
                    if (v) chosen.add(i) else chosen.remove(i)
                    P3.setSalFormulas(act, chosen)
                }
            }, 4)
        }

        // الصوت
        box.put(act.tv("الصوت", 16f, "#0B3D2E", true), 18)
        val sTv = act.tv(soundText(), 14f, "#555555")
        box.put(sTv, 2)
        box.put(act.tv("بدون ملفات صوتية يُنطق النص بالصوت العربي المثبت في هاتفك. وإذا أضفت ملفات صوتية بصيغ مختلفة سيُشغَّل واحد منها عشوائياً بدل النطق الآلي.", 12f, "#777777"), 2)
        box.put(act.btn("➕ إضافة صوت (ملف من الجهاز أو رابط)") {
            Pickers.audio(act, "salawat") { f: File, n: String ->
                P3.addSalFile(act, f.absolutePath); sTv.text = soundText(); act.toast("تمت الإضافة: $n")
            }
        }, 6)
        box.put(act.btn("🗑 مسح الأصوات المضافة (والرجوع للنطق الآلي)") {
            P3.clearSalFiles(act); sTv.text = soundText()
        }, 4)
        box.put(act.btn("▶ تجربة الآن") { SalawatService.start(act, true) }, 10)
        box.put(act.btn("⏹ إيقاف") { SalawatService.stop(act) }, 4)
        return box
    }

    private fun hoursText() = "يعمل من الساعة ${P3.salStart(act)}:00 إلى ${P3.salEnd(act)}:00"

    private fun soundText(): String {
        val n = P3.salFiles(act).size
        return if (n == 0) "الصوت الحالي: نطق آلي" else "الصوت الحالي: $n ملف صوتي مضاف"
    }
}
