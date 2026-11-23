package com.example.quranwidget

import android.app.AlertDialog
import android.app.ProgressDialog
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.net.URL

class LibraryTab(a: MainActivity) : Tab(a) {
    override val cardWrap: Boolean get() = false

    private var nowTv: TextView? = null
    private var nowCard: LinearLayout? = null

    override fun build(): View {
        val box = act.vbox(0)

        // ---------- شريط التشغيل الحالي ----------
        val now = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            background = Art.hero(act)
            setPadding(act.dp(16), act.dp(12), act.dp(16), act.dp(12))
            round(18)
            visibility = View.GONE
        }
        val nTv = act.tv("", 16f, "#FFFFFF", true)
        nowTv = nTv
        now.addView(nTv, LinearLayout.LayoutParams(0, -2, 1f))
        now.addView(act.btn("⏹ إيقاف") { MediaService.stop(act); MushafService.action(act, "STOP") })
        nowCard = now
        box.put(now, 0)

        // ---------- الإذاعة ----------
        val radio = section("📻", "إذاعة القرآن الكريم", "بث مباشر لإذاعات القرآن الكريم، يعمل والشاشة مقفولة.")
        radio.put(act.btn("📡 اختيار إذاعة وتشغيلها") { radios() }, 10)
        radio.put(act.btn("➕ إضافة إذاعة برابط البث") { addRadio() }, 6)
        box.put(radio, 14)

        // ---------- القصص ----------
        val stories = section("🕌", "قصص الأنبياء", "قصص الأنبياء كما وردت في القرآن الكريم، بتلاوة القارئ الذي تختاره، مع التفسير والقراءة.")
        stories.put(act.btn("📜 اختيار قصة") { entries("قصص الأنبياء", Library.stories) }, 10)
        stories.put(act.btn("🎙 مقاطعي الصوتية (قصص)") { clips("stories", "قصص الأنبياء") }, 6)
        box.put(stories, 14)

        // ---------- علامات الساعة ----------
        val signs = section("⏳", "علامات الساعة", "الآيات الواردة في أشراط الساعة تُسمع بتلاوة القارئ، والأحاديث الصحيحة نصاً مع النطق.")
        signs.put(act.btn("📖 آيات علامات الساعة") { entries("آيات علامات الساعة", Library.signsAyat) }, 10)
        signs.put(act.btn("📚 أحاديث علامات الساعة") { texts("أحاديث علامات الساعة", Library.signsTexts) }, 6)
        signs.put(act.btn("🎙 مقاطعي الصوتية (علامات الساعة)") { clips("signs", "علامات الساعة") }, 6)
        box.put(signs, 14)

        // ---------- الرقية ----------
        val ruq = section("🛡", "الرقية الشرعية", "آيات الرقية الشرعية تُسمع بتلاوة القارئ، ويمكن تحميلها لتعمل بدون إنترنت.")
        ruq.put(act.btn("🎧 الرقية الشرعية (صوتية)") { entries("الرقية الشرعية", Library.ruqyah) }, 10)
        ruq.put(act.btn("🤲 أدعية الرقية النبوية") { texts("أدعية الرقية النبوية", Library.ruqyahTexts) }, 6)
        ruq.put(act.btn("🎙 مقاطعي الصوتية (رقية)") { clips("ruqyah", "الرقية الشرعية") }, 6)
        box.put(ruq, 14)

        box.put(act.tv("القارئ الحالي: ${MushafAudio.reciterName(act)} (يتغير من تبويب المصحف)", 12f, "#6B7A70").apply { gravity = Gravity.CENTER }, 14)
        return box
    }

    private fun section(icon: String, title: String, desc: String): LinearLayout {
        val c = act.card(16)
        val head = act.hbox()
        head.addView(act.tv(icon, 30f).apply { setPadding(0, 0, act.dp(10), 0) })
        head.addView(act.tv(title, 22f, "#0B3D2E", true))
        c.put(head, 0)
        c.put(act.tv(desc, 13f, "#6B7A70"), 4)
        return c
    }

    private fun refreshNow() {
        val t = nowTv ?: return
        val live = LivePlayer.playing
        val mushaf = MushafPlayer.playing
        nowCard?.visibility = if (live || mushaf) View.VISIBLE else View.GONE
        t.text = if (live) "▶ ${LivePlayer.title}" else "▶ تلاوة سورة ${Quran.names[MushafPlayer.surah - 1]} : ${MushafPlayer.ayah}"
    }

    override fun onShow() {
        LivePlayer.onChange = { refreshNow() }
        MushafPlayer.onChange = { _, _, _ -> refreshNow() }
        refreshNow()
    }

    override fun onHide() { LivePlayer.onChange = null; MushafPlayer.onChange = null }

    // ================= الإذاعات =================
    private fun radios() {
        act.toast("جاري تحميل الإذاعات…")
        Thread {
            var list: List<Pair<String, String>> = emptyList()
            try {
                val txt = URL("https://www.mp3quran.net/api/v3/radios?language=ar").readText()
                val arr = JSONObject(txt).getJSONArray("radios")
                list = (0 until arr.length()).map {
                    val o = arr.getJSONObject(it); o.getString("name") to o.getString("url")
                }
                P.sp(act).edit().putString("radios_cache", txt).apply()
            } catch (e: Exception) {
                try {
                    val arr = JSONObject(P.sp(act).getString("radios_cache", "{}") ?: "{}").getJSONArray("radios")
                    list = (0 until arr.length()).map {
                        val o = arr.getJSONObject(it); o.getString("name") to o.getString("url")
                    }
                } catch (e2: Exception) { }
            }
            val custom = Catalog.all(act, "radio").map { it.name to it.url }
            val all = custom + list
            act.runOnUiThread {
                if (all.isEmpty()) { act.toast("تعذر تحميل الإذاعات. تأكد من الإنترنت."); return@runOnUiThread }
                AlertDialog.Builder(act).setTitle("اختر الإذاعة")
                    .setItems(all.map { it.first }.toTypedArray()) { _, i ->
                        MediaService.play(act, all[i].second, all[i].first)
                    }.show()
            }
        }.start()
    }

    private fun addRadio() {
        val box = act.vbox(16)
        val name = EditText(act).apply { hint = "اسم الإذاعة" }
        val url = EditText(act).apply { hint = "رابط البث المباشر"; setSingleLine(true) }
        box.put(name); box.put(url)
        AlertDialog.Builder(act).setTitle("إضافة إذاعة").setView(box)
            .setPositiveButton("حفظ وتشغيل") { _, _ ->
                val u = url.text.toString().trim()
                val n = name.text.toString().trim().ifEmpty { "إذاعة مضافة" }
                if (u.startsWith("http")) {
                    Catalog.addCustom(act, Item(n, u, "radio"))
                    MediaService.play(act, u, n)
                } else act.toast("الرابط غير صحيح")
            }.setNegativeButton("إلغاء", null).show()
    }

    // ================= القوائم بالتلاوة =================
    private fun entries(title: String, list: List<Entry>) {
        AlertDialog.Builder(act).setTitle(title)
            .setItems(list.map { it.title }.toTypedArray()) { _, i -> entryMenu(list[i]) }.show()
    }

    private fun entryMenu(e: Entry) {
        val items = arrayOf("▶ استماع", "⬇ تحميل للاستماع بدون إنترنت", "📖 قراءة في المصحف")
        AlertDialog.Builder(act).setTitle(e.title).setItems(items) { _, i ->
            when (i) {
                0 -> {
                    MushafService.playQueue(act, e.spec, e.title, MushafAudio.reciter(act))
                    act.toast("بتلاوة ${MushafAudio.reciterName(act)}")
                }
                1 -> download(e)
                else -> {
                    val first = Library.parse(act, e.spec).firstOrNull() ?: return@setItems
                    val s = first.first
                    val pos = first.second + (if (s != 1 && s != 9) 1 else 0)
                    P.sp(act).edit().putInt("m_surah", s).putInt("m_pos", (pos - 2).coerceAtLeast(0)).apply()
                    act.show(1)
                }
            }
        }.show()
    }

    private fun download(e: Entry) {
        val pairs = Library.parse(act, e.spec)
        val pd = ProgressDialog(act)
        pd.setMessage("تحميل: ${e.title}")
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        pd.max = pairs.size
        pd.setCancelable(false)
        pd.show()
        MushafAudio.downloadPairs(act, MushafAudio.reciter(act), pairs, { d, _ -> pd.progress = d }, { ok ->
            pd.dismiss()
            act.toast(if (ok) "تم التحميل، يعمل بدون إنترنت" else "تعذر إكمال التحميل، تأكد من الإنترنت")
        })
    }

    // ================= النصوص (أحاديث وأدعية) =================
    private fun texts(title: String, list: List<TextItem>) {
        AlertDialog.Builder(act).setTitle(title)
            .setItems(list.map { it.title }.toTypedArray()) { _, i -> showText(list[i]) }.show()
    }

    private fun showText(t: TextItem) {
        val box = act.vbox(16)
        box.put(act.tv(t.text, 21f, "#0B3D2E").apply {
            typeface = Fonts.amiri(act)
            setLineSpacing(0f, 1.5f)
            setTextIsSelectable(true)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            textDirection = View.TEXT_DIRECTION_RTL
        }, 0)
        box.put(act.tv("(${t.source})", 14f, "#8A5A00", true), 8)
        val d = AlertDialog.Builder(act).setTitle(t.title)
            .setView(ScrollView(act).apply { addView(box) })
            .setPositiveButton("إغلاق", null)
            .setNeutralButton("🔊 نطق") { _, _ ->
                Speaker.speak(act, t.text) { act.toast("صوت النطق العربي غير مثبت في هاتفك. ثبّته من إعدادات الهاتف.") }
            }
            .create()
        d.setOnDismissListener { Speaker.stop() }
        d.show()
    }

    // ================= مقاطعي الصوتية =================
    private fun clips(kind: String, title: String) {
        val items = Catalog.all(act, kind)
        val labels = items.map { (if (Catalog.file(act, it).exists()) "✓ " else "⬇ ") + it.name } +
            listOf("➕ إضافة مقطع (ملف من جهازك أو رابط)")
        AlertDialog.Builder(act).setTitle("مقاطعي: $title").setItems(labels.toTypedArray()) { _, i ->
            if (i < items.size) {
                Pickers.fetch(act, items[i]) { f: File, n: String -> MediaService.play(act, f.absolutePath, n) }
            } else {
                Pickers.audio(act, kind) { f: File, n: String -> MediaService.play(act, f.absolutePath, n) }
            }
        }.show()
    }
}
