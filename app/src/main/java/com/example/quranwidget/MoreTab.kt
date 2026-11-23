package com.example.quranwidget

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

class MoreTab(a: MainActivity) : Tab(a) {
    override val cardWrap: Boolean get() = false

    override fun build(): View {
        val box = act.vbox(4)
        val tiles = listOf(
            Triple("🔔", "الأذان والمؤذنون", 5),
            Triple("🧭", "اتجاه القبلة", 6),
            Triple("🗓", "الإمساكية والتقويم", 7),
            Triple("🌙", "رمضان\nالمدفع والمسحراتي", 8),
            Triple("🤍", "الصلاة على النبي ﷺ", 9),
            Triple("🖼", "آيات الودجت\nوخلفية القفل", 10)
        )
        var i = 0
        while (i < tiles.size) {
            val row = act.hbox()
            for (k in 0..1) {
                val t = tiles[i + k]
                val c = LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = Art.cardBg(act)
                    elevation = act.dp(2).toFloat()
                    setPadding(act.dp(10), act.dp(22), act.dp(10), act.dp(22))
                    setOnClickListener { act.show(t.third) }
                }
                c.addView(act.tv(t.first, 40f).apply { gravity = Gravity.CENTER })
                c.addView(act.tv(t.second, 16f, "#0B3D2E", true).apply { gravity = Gravity.CENTER })
                val lp = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(act.dp(6), act.dp(6), act.dp(6), act.dp(6)) }
                row.addView(c, lp)
            }
            box.addView(row)
            i += 2
        }
        box.put(act.tv("آيات والأذان\nالمواقيت بطريقة الهيئة المصرية للمساحة", 12f, "#6B7A70").apply { gravity = Gravity.CENTER }, 16)
        return box
    }
}
