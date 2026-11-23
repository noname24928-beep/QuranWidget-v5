package com.example.quranwidget

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

private class Row(val ayah: Int, val text: String)

class MushafTab(a: MainActivity) : Tab(a) {
    override val scrolls: Boolean get() = false

    private var surah = P.sp(a).getInt("m_surah", 1)
    private var cur = -1
    private var fontSize = P.sp(a).getFloat("m_font", 26f)
    private var rows: List<Row> = emptyList()
    private var list: ListView? = null
    private var playBtn: Button? = null
    private var surahBtn: Button? = null
    private var recBtn: Button? = null
    private var tafBtn: Button? = null
    private lateinit var tf: Typeface

    private val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(p: Int): Any = rows[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup?): View {
            val r = rows[p]
            val t = (v as? TextView) ?: TextView(act).apply {
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                textDirection = View.TEXT_DIRECTION_RTL
                setPadding(act.dp(14), act.dp(10), act.dp(14), act.dp(10))
                typeface = tf
            }
            when {
                r.ayah == 0 -> {
                    t.text = r.text; t.textSize = 22f; t.gravity = Gravity.CENTER
                    t.setTextColor(Color.WHITE); t.setBackgroundColor(Color.parseColor("#0B3D2E"))
                }
                r.ayah == -1 -> {
                    t.text = r.text; t.textSize = fontSize; t.gravity = Gravity.CENTER
                    t.setTextColor(Color.parseColor("#0B3D2E")); t.setBackgroundColor(Color.TRANSPARENT)
                }
                else -> {
                    t.text = r.text + " \uFD3F" + arNum(r.ayah) + "\uFD3E"
                    t.textSize = fontSize; t.gravity = Gravity.START
                    t.setLineSpacing(0f, 1.5f)
                    t.setTextColor(Color.parseColor("#1B1B1B"))
                    t.setBackgroundColor(if (r.ayah == cur) Color.parseColor("#FFE08A") else Color.TRANSPARENT)
                }
            }
            return t
        }
    }

    private fun arNum(n: Int) = n.toString().map { '٠' + (it - '0') }.joinToString("")

    private fun loadRows() {
        val out = ArrayList<Row>()
        out.add(Row(0, "سورة ${Quran.names[surah - 1]}"))
        if (surah != 1 && surah != 9) out.add(Row(-1, "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"))
        for ((n, t) in Quran.verses(act, surah)) out.add(Row(n, t))
        rows = out
    }

    private fun small(label: String, onClick: () -> Unit): Button = act.btn(label, onClick).apply {
        textSize = 13f
        minWidth = 0
        minHeight = 0
        setPadding(act.dp(8), act.dp(6), act.dp(8), act.dp(6))
    }

    override fun build(): View {
        tf = act.resources.getFont(R.font.amiri_quran)
        if (MushafPlayer.active) { surah = MushafPlayer.surah; if (MushafPlayer.playing) cur = MushafPlayer.ayah }
        loadRows()

        val root = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val bar = act.vbox(6)

        val r1 = act.hbox()
        surahBtn = small("") { pickSurah() }
        recBtn = small("") { pickReciter() }
        r1.addView(surahBtn!!, LinearLayout.LayoutParams(0, -2, 1f))
        r1.addView(recBtn!!, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(r1)

        val r2 = act.hbox()
        playBtn = small("▶") { onPlay() }
        r2.addView(small("⏮") { if (MushafPlayer.active) MushafService.action(act, "PREV") })
        r2.addView(playBtn!!)
        r2.addView(small("⏭") { if (MushafPlayer.active) MushafService.action(act, "NEXT") })
        r2.addView(small("⏹") { MushafService.action(act, "STOP") })
        r2.addView(small("⬇ تحميل السورة") { download() })
        bar.put(r2, 4)

        val r3 = act.hbox()
        tafBtn = small("") { pickTafsir(null) }
        r3.addView(tafBtn!!, LinearLayout.LayoutParams(0, -2, 1f))
        r3.addView(small("A−") { setFont(fontSize - 2f) })
        r3.addView(small("A+") { setFont(fontSize + 2f) })
        r3.addView(small("🔖") { bookmarkMenu() })
        bar.put(r3, 4)

        bar.put(Switch(act).apply {
            text = "الانتقال للسورة التالية تلقائياً"
            textSize = 13f
            isChecked = P.sp(act).getBoolean("mnext", false)
            setOnCheckedChangeListener { _, v -> P.sp(act).edit().putBoolean("mnext", v).apply() }
        }, 2)
        root.addView(bar)

        list = ListView(act).apply {
            adapter = this@MushafTab.adapter
            divider = null
            setOnItemClickListener { _, _, pos, _ -> rows.getOrNull(pos)?.let { if (it.ayah > 0) ayahMenu(it.ayah) } }
        }
        root.addView(list!!, LinearLayout.LayoutParams(-1, 0, 1f))

        refreshLabels()
        val savedSurah = P.sp(act).getInt("m_surah", 1)
        val savedPos = P.sp(act).getInt("m_pos", 0)
        list!!.post {
            if (cur > 0) scrollTo(cur)
            else if (savedSurah == surah) list?.setSelection(savedPos.coerceAtMost(rows.size - 1))
        }
        if (P.sp(act).getString("mreciters", null) == null) MushafAudio.refreshReciters(act) { }
        return root
    }

    private fun refreshLabels() {
        surahBtn?.text = "سورة ${Quran.names[surah - 1]} ▾"
        recBtn?.text = "${MushafAudio.reciterName(act)} ▾"
        tafBtn?.text = "${Tafsir.name(tafsirSlug()).substringBefore(" (")} ▾"
        playBtn?.text = if (MushafPlayer.playing) "⏸" else "▶"
    }

    private fun tafsirSlug() = P.sp(act).getString("tafsir", "muyassar") ?: "muyassar"

    private fun setFont(v: Float) {
        fontSize = v.coerceIn(16f, 44f)
        P.sp(act).edit().putFloat("m_font", fontSize).apply()
        adapter.notifyDataSetChanged()
    }

    private fun scrollTo(ayah: Int) {
        val i = rows.indexOfFirst { it.ayah == ayah }
        if (i >= 0) list?.smoothScrollToPositionFromTop(i, act.dp(40), 300)
    }

    private fun openSurah(s: Int, ayah: Int = 0) {
        surah = s
        cur = -1
        loadRows()
        adapter.notifyDataSetChanged()
        refreshLabels()
        if (ayah > 0) list?.post { list?.setSelection(rows.indexOfFirst { it.ayah == ayah }.coerceAtLeast(0)) }
        else list?.setSelection(0)
    }

    private fun pickSurah() {
        val labels = Array(114) { "${it + 1} - ${Quran.names[it]}" }
        AlertDialog.Builder(act).setTitle("اختر السورة").setItems(labels) { _, i -> openSurah(i + 1) }.show()
    }

    private fun pickReciter() {
        val rs = MushafAudio.reciters(act)
        val labels = (rs.map { it.second } + "🔄 تحديث قائمة القراء من الإنترنت").toTypedArray()
        AlertDialog.Builder(act).setTitle("اختر القارئ").setItems(labels) { _, i ->
            if (i < rs.size) {
                MushafAudio.setReciter(act, rs[i].first, rs[i].second)
                refreshLabels()
                act.toast("القارئ: ${rs[i].second}")
            } else {
                act.toast("جاري التحديث…")
                MushafAudio.refreshReciters(act) { ok ->
                    if (ok) { act.toast("تم التحديث"); pickReciter() } else act.toast("تعذر التحديث")
                }
            }
        }.show()
    }

    private fun onPlay() {
        when {
            MushafPlayer.playing -> MushafService.action(act, "PAUSE")
            MushafPlayer.active && MushafPlayer.surah == surah -> MushafService.action(act, "RESUME")
            else -> {
                val pos = list?.firstVisiblePosition ?: 0
                val a = rows.drop(pos).firstOrNull { it.ayah > 0 }?.ayah ?: 1
                MushafService.play(act, surah, a, MushafAudio.reciter(act))
            }
        }
    }

    private fun download() {
        val rec = MushafAudio.reciter(act)
        val n = Quran.count(act, surah)
        val pd = ProgressDialog(act)
        pd.setMessage("تحميل سورة ${Quran.names[surah - 1]} - ${MushafAudio.reciterName(act)}")
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        pd.max = n
        pd.setCancelable(false)
        pd.show()
        MushafAudio.download(act, rec, surah, { a, _ -> pd.progress = a }, { ok ->
            pd.dismiss()
            act.toast(if (ok) "تم التحميل، السورة تعمل بدون إنترنت" else "تعذر إكمال التحميل، تأكد من الإنترنت")
        })
    }

    private fun ayahMenu(a: Int) {
        val items = arrayOf("📖 تفسير الآية", "▶ تشغيل من هذه الآية", "🔖 حفظ علامة هنا", "📋 نسخ الآية")
        AlertDialog.Builder(act).setTitle("سورة ${Quran.names[surah - 1]} : $a").setItems(items) { _, i ->
            when (i) {
                0 -> showTafsir(a)
                1 -> MushafService.play(act, surah, a, MushafAudio.reciter(act))
                2 -> {
                    P.sp(act).edit().putInt("bm_s", surah).putInt("bm_a", a).apply()
                    act.toast("تم حفظ العلامة")
                }
                else -> {
                    val t = rows.firstOrNull { it.ayah == a }?.text ?: ""
                    (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("ayah", "$t\n(سورة ${Quran.names[surah - 1]} : $a)"))
                    act.toast("تم النسخ")
                }
            }
        }.show()
    }

    private fun bookmarkMenu() {
        val s = P.sp(act).getInt("bm_s", 0)
        val a = P.sp(act).getInt("bm_a", 0)
        if (s == 0) { act.toast("لم تحفظ علامة بعد. اضغط على أي آية ثم «حفظ علامة»"); return }
        openSurah(s, a)
        act.toast("العلامة: سورة ${Quran.names[s - 1]} : $a")
    }

    private fun pickTafsir(then: (() -> Unit)?) {
        val labels = Tafsir.options.map { it.second }.toTypedArray()
        AlertDialog.Builder(act).setTitle("اختر التفسير").setItems(labels) { _, i ->
            P.sp(act).edit().putString("tafsir", Tafsir.options[i].first).apply()
            refreshLabels()
            then?.invoke()
        }.show()
    }

    private fun showTafsir(a: Int) {
        val slug = tafsirSlug()
        if (slug == "ibnkathir") act.toast("جاري التحميل…")
        Tafsir.fetch(act, slug, surah, a) { txt ->
            val tv = act.tv(txt, 18f).apply {
                setLineSpacing(0f, 1.35f)
                setTextIsSelectable(true)
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                textDirection = View.TEXT_DIRECTION_RTL
            }
            val box = act.vbox(16)
            val verse = rows.firstOrNull { it.ayah == a }?.text ?: ""
            box.put(act.tv("\uFD3F $verse \uFD3E", 22f, "#0B3D2E").apply { typeface = tf; gravity = Gravity.CENTER })
            box.put(tv0(), 4)
            box.put(tv, 8)
            val sv = ScrollView(act).apply { addView(box) }
            AlertDialog.Builder(act)
                .setTitle("${Tafsir.name(slug).substringBefore(" (")} - ${Quran.names[surah - 1]} : $a")
                .setView(sv)
                .setPositiveButton("إغلاق", null)
                .setNeutralButton("تغيير التفسير") { _, _ -> pickTafsir { showTafsir(a) } }
                .show()
        }
    }

    private fun tv0(): View = View(act).apply { setBackgroundColor(Color.parseColor("#DDDDDD")); minimumHeight = act.dp(1) }

    private val listener: (Int, Int, Boolean) -> Unit = { s, a, playing ->
        if (s != surah) openSurah(s)
        cur = if (playing || MushafPlayer.active) a else -1
        adapter.notifyDataSetChanged()
        if (cur > 0 && playing) scrollTo(cur)
        playBtn?.text = if (playing) "⏸" else "▶"
    }

    override fun onShow() { MushafPlayer.onChange = listener; refreshLabels() }

    override fun onHide() {
        MushafPlayer.onChange = null
        P.sp(act).edit().putInt("m_surah", surah).putInt("m_pos", list?.firstVisiblePosition ?: 0).apply()
    }
}
