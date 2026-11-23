package com.example.quranwidget

import android.app.AlertDialog
import android.graphics.Color
import android.media.MediaPlayer
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import java.io.File

object DhikrAudio {
    private var mp: MediaPlayer? = null
    fun play(f: File, onDone: () -> Unit) {
        stop()
        try {
            val m = MediaPlayer()
            mp = m
            m.setDataSource(f.path)
            m.setOnPreparedListener { it.start() }
            m.setOnCompletionListener { onDone() }
            m.setOnErrorListener { _, _, _ -> onDone(); true }
            m.prepareAsync()
        } catch (e: Exception) { onDone() }
    }
    fun stop() {
        try { mp?.release() } catch (e: Exception) { }
        mp = null
    }
}

class AdhkarTab(a: MainActivity) : Tab(a) {
    override fun build(): View {
        val box = act.vbox()
        box.put(act.tv("الأذكار", 20f, "#0B3D2E", true))

        box.put(act.tv("تنبيهات تلقائية (شغّلها أو اقفلها)", 16f, "#0B3D2E", true), 14)
        fun sw(label: String, key: String, on: Boolean) = Switch(act).apply {
            text = label
            isChecked = on
            setOnCheckedChangeListener { _, v -> P3.put(act, key, v); Scheduler.scheduleAll(act) }
        }
        box.put(sw("أذكار الصباح (بعد الفجر بنصف ساعة)", "ad_morning", P3.morningOn(act)), 6)
        box.put(sw("أذكار المساء (بعد العصر بنصف ساعة)", "ad_evening", P3.eveningOn(act)), 6)
        box.put(sw("أذكار النوم (بعد العشاء بساعة)", "ad_sleep", P3.sleepOn(act)), 6)
        box.put(sw("ذكر دوري على مدار اليوم", "ad_periodic", P3.dhikrOn(act)), 6)

        val mTv = act.tv("كل ${P3.dhikrMin(act)} دقيقة", 15f)
        val row = act.hbox()
        row.addView(mTv, LinearLayout.LayoutParams(0, -2, 1f))
        fun step(d: Int) {
            val v = (P3.dhikrMin(act) + d).coerceIn(15, 480)
            P3.putInt(act, "ad_periodic_min", v)
            mTv.text = "كل $v دقيقة"
            Scheduler.scheduleAll(act)
        }
        row.addView(act.btn("−") { step(-15) })
        row.addView(act.btn("+") { step(15) })
        box.put(row, 2)
        box.put(act.tv("الذكر الدوري يظهر بين الخامسة صباحاً والحادية عشرة مساءً. التنبيهات إشعارات، تأكد من السماح بالإشعارات من تبويب المواقيت.", 12f, "#777777"), 4)

        box.put(act.tv("تصفح الأذكار (حصن المسلم)", 16f, "#0B3D2E", true), 20)
        box.put(act.btn("☀ أذكار الصباح والمساء") { open("الصباح والمساء") }, 6)
        box.put(act.btn("🌙 أذكار النوم") { open("أذكار النوم") }, 4)
        box.put(act.btn("🕌 الأذكار بعد السلام من الصلاة") { open("الأذكار بعد السلام من الصلاة") }, 4)
        box.put(act.btn("📚 كل الأقسام") {
            val cats = Adhkar.all(act)
            AlertDialog.Builder(act).setTitle("اختر القسم")
                .setItems(cats.map { it.name }.toTypedArray()) { _, i -> show(cats[i]) }.show()
        }, 4)
        return box
    }

    private fun open(part: String) {
        val c = Adhkar.byName(act, part)
        if (c != null) show(c) else act.toast("القسم غير موجود")
    }

    private fun show(cat: DhikrCat) {
        val box = act.vbox(14)
        val audioBtn = act.btn("🔊 سماع القسم (يُحمّل أول مرة)") { }
        audioBtn.setOnClickListener {
            if (cat.audio.isEmpty()) { act.toast("لا يوجد صوت لهذا القسم"); return@setOnClickListener }
            val item = Item(cat.name, Adhkar.AUDIO_BASE + cat.audio, "adhkar")
            Pickers.fetch(act, item) { f, _ ->
                DhikrAudio.play(f) { act.runOnUiThread { audioBtn.text = "🔊 سماع القسم (يُحمّل أول مرة)" } }
                audioBtn.text = "⏹ إيقاف الصوت"
            }
        }
        box.put(audioBtn)
        for (d in cat.items) {
            box.put(act.tv(d.text, 18f).apply {
                setLineSpacing(0f, 1.35f)
                setTextIsSelectable(true)
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                textDirection = View.TEXT_DIRECTION_RTL
            }, 14)
            if (d.count > 1) box.put(act.tv("التكرار: ${d.count} مرات", 13f, "#8A5A00", true), 2)
            box.put(View(act).apply { setBackgroundColor(Color.parseColor("#DDDDDD")); minimumHeight = act.dp(1) }, 10)
        }
        AlertDialog.Builder(act).setTitle(cat.name)
            .setView(ScrollView(act).apply { addView(box) })
            .setPositiveButton("إغلاق", null)
            .setOnDismissListener { DhikrAudio.stop() }
            .show()
    }

    override fun onHide() { DhikrAudio.stop() }
}
