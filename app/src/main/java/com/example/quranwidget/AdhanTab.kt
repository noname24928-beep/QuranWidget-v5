package com.example.quranwidget

import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import java.io.File

class AdhanTab(a: MainActivity) : Tab(a) {
    override fun build(): View {
        val box = act.vbox()

        // ---- قبل الأذان ----
        box.put(act.tv("قبل الأذان", 19f, "#0B3D2E", true))
        box.put(Switch(act).apply {
            text = "تشغيل تواشيح/قرآن قبل الأذان"
            isChecked = P.preOn(act)
            setOnCheckedChangeListener { _, v -> P.setPreOn(act, v); Scheduler.scheduleAll(act) }
        })
        val minTv = act.tv("قبل الأذان بـ ${P.preMin(act)} دقيقة", 15f)
        val minRow = act.hbox()
        minRow.addView(minTv, LinearLayout.LayoutParams(0, -2, 1f))
        minRow.addView(act.btn("−") { P.setPreMin(act, P.preMin(act) - 1); minTv.text = "قبل الأذان بـ ${P.preMin(act)} دقيقة"; Scheduler.scheduleAll(act) })
        minRow.addView(act.btn("+") { P.setPreMin(act, P.preMin(act) + 1); minTv.text = "قبل الأذان بـ ${P.preMin(act)} دقيقة"; Scheduler.scheduleAll(act) })
        box.put(minRow)
        val preTv = act.tv("الصوت: ${P.preName(act) ?: "لم يتم الاختيار"}", 14f, "#555555")
        box.put(preTv)
        val onPre = { f: File, n: String ->
            P.setPre(act, f.absolutePath, n); preTv.text = "الصوت: $n"; Scheduler.scheduleAll(act)
        }
        box.put(act.btn("اختيار تواشيح / ملف") { Pickers.audio(act, "pre", onPre) })
        box.put(act.btn("اختيار قرآن (قارئ وسورة)") { Pickers.quran(act, "pre", onPre) }, 4)
        box.put(act.btn("▶ تجربة") {
            val f = P.preFile(act)
            if (f == null) act.toast("اختر صوتاً أولاً") else AthanService.start(act, AthanService.KIND_TEST, 0, f)
        }, 4)

        // ---- المؤذنون ----
        box.put(act.tv("المؤذن لكل صلاة", 19f, "#0B3D2E", true), 24)
        box.put(act.tv("إن لم تختر مؤذناً سيُستخدم صوت المنبه الافتراضي للهاتف.", 12f, "#777777"), 2)

        for (p in 0..4) {
            box.put(Switch(act).apply {
                text = "أذان ${P.prayerNames[p]}"
                textSize = 17f
                isChecked = P.adhanOn(act, p)
                setOnCheckedChangeListener { _, v -> P.setAdhanOn(act, p, v); Scheduler.scheduleAll(act) }
            }, 14)
            val st = act.tv("المؤذن: ${P.adhanName(act, p) ?: "الافتراضي"}", 14f, "#555555")
            box.put(st, 2)
            val row = act.hbox()
            row.addView(act.btn("اختيار") {
                Pickers.audio(act, "adhan") { f, n ->
                    P.setAdhan(act, p, f.absolutePath, n); st.text = "المؤذن: $n"
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(act.btn("▶ تجربة") {
                AthanService.start(act, AthanService.KIND_TEST, p, P.adhanFile(act, p))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            box.put(row, 2)
        }

        box.put(act.btn("تعيين نفس المؤذن لكل الصلوات") {
            Pickers.audio(act, "adhan") { f, n ->
                for (p in 0..4) P.setAdhan(act, p, f.absolutePath, n)
                act.show(5)
            }
        }, 18)
        box.put(act.btn("⏹ إيقاف الصوت") { AthanService.stop(act) }, 4)
        return box
    }
}
