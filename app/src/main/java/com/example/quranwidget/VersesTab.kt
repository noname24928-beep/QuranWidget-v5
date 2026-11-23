package com.example.quranwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class VersesTab(a: MainActivity) : Tab(a) {
    private lateinit var occTv: TextView
    private lateinit var verseTv: TextView
    private lateinit var refTv: TextView

    override fun build(): View {
        val col = act.vbox()
        val tf = act.resources.getFont(R.font.amiri_quran)
        col.put(act.tv("آيات الودجت", 20f, "#0B3D2E", true))

        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.widget_bg)
            setPadding(act.dp(18), act.dp(18), act.dp(18), act.dp(18))
        }
        occTv = TextView(act).apply { setTextColor(Color.parseColor("#FFD98E")); textSize = 13f; gravity = Gravity.CENTER }
        verseTv = TextView(act).apply { setTextColor(Color.WHITE); textSize = 24f; typeface = tf; gravity = Gravity.CENTER }
        refTv = TextView(act).apply { setTextColor(Color.parseColor("#CFE8D5")); textSize = 13f; gravity = Gravity.CENTER }
        card.addView(occTv); card.put(verseTv, 8); card.put(refTv, 8)
        col.put(card, 12)

        col.put(act.btn("آية أخرى") { Quran.current(act, true); show(); VerseWidget.refreshAll(act, false) }, 10)

        col.put(act.tv("نوع الآيات", 16f, "#1B1B1B", true), 16)
        val rg = RadioGroup(act)
        val r0 = RadioButton(act).apply { text = "مناسبة لليوم (الجمعة، رمضان، الأعياد…) وإلا متنوعة"; id = View.generateViewId() }
        val r1 = RadioButton(act).apply { text = "متنوعة من كامل المصحف"; id = View.generateViewId() }
        rg.addView(r0); rg.addView(r1)
        rg.check(if (Quran.getMode(act) == 0) r0.id else r1.id)
        rg.setOnCheckedChangeListener { _, id ->
            Quran.setMode(act, if (id == r0.id) 0 else 1); show(); VerseWidget.refreshAll(act, false)
        }
        col.put(rg, 4)

        col.put(act.btn("إضافة الودجت للشاشة الرئيسية") {
            val mgr = AppWidgetManager.getInstance(act)
            if (mgr.isRequestPinAppWidgetSupported) {
                mgr.requestPinAppWidget(ComponentName(act, VerseWidget::class.java), null, null)
            } else act.toast("اضغط مطولاً على الشاشة الرئيسية ← الودجات ← آيات")
        }, 16)

        col.put(act.tv("أندرويد لا يسمح غالباً بودجت على شاشة القفل، لذلك يمكن وضع الآية كخلفية للقفل (سيستبدل خلفية القفل الحالية).", 13f, "#555555"), 16)
        col.put(act.btn("تعيين الآية الآن كخلفية لشاشة القفل") {
            Thread {
                try { LockScreen.apply(act, false); act.runOnUiThread { act.toast("تم تعيين خلفية القفل") } }
                catch (e: Exception) { act.runOnUiThread { act.toast("تعذّر تعيين الخلفية") } }
            }.start()
        }, 6)

        val sp = act.getSharedPreferences("quran", android.content.Context.MODE_PRIVATE)
        col.put(Switch(act).apply {
            text = "تغيير خلفية القفل تلقائياً كل 6 ساعات"
            isChecked = sp.getBoolean("lock", false)
            setOnCheckedChangeListener { _, on ->
                sp.edit().putBoolean("lock", on).apply()
                val wm = WorkManager.getInstance(act)
                if (on) {
                    wm.enqueueUniquePeriodicWork(
                        "lock", ExistingPeriodicWorkPolicy.UPDATE,
                        PeriodicWorkRequestBuilder<LockWorker>(6, TimeUnit.HOURS).build()
                    )
                    act.toast("تم التفعيل")
                } else wm.cancelUniqueWork("lock")
            }
        }, 12)
        show()
        return col
    }

    private fun show() {
        val v = Quran.current(act)
        verseTv.text = v.display
        refTv.text = v.ref
        occTv.text = v.occasion ?: ""
        occTv.visibility = if (v.occasion != null) View.VISIBLE else View.GONE
    }
}
