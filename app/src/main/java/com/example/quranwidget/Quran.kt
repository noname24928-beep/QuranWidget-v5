package com.example.quranwidget

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

data class Verse(val surah: Int, val ayah: Int, val text: String, val occasion: String?) {
    val ref: String get() = "سورة ${Quran.names[surah - 1]} : $ayah"
    val display: String get() = "\uFD3F $text \uFD3E"
}

private class Occ(val title: String, val refs: List<Triple<Int, Int, Int>>)

private fun r(s: Int, a: Int, b: Int = a) = Triple(s, a, b)

object Quran {
    val names = listOf(
        "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال", "التوبة", "يونس",
        "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء", "الكهف", "مريم", "طه",
        "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء", "النمل", "القصص", "العنكبوت", "الروم",
        "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر", "يس", "الصافات", "ص", "الزمر", "غافر",
        "فصلت", "الشورى", "الزخرف", "الدخان", "الجاثية", "الأحقاف", "محمد", "الفتح", "الحجرات", "ق",
        "الذاريات", "الطور", "النجم", "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة",
        "الصف", "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة", "المعارج",
        "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات", "النبأ", "النازعات", "عبس",
        "التكوير", "الانفطار", "المطففين", "الانشقاق", "البروج", "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد",
        "الشمس", "الليل", "الضحى", "الشرح", "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات",
        "القارعة", "التكاثر", "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون", "النصر",
        "المسد", "الإخلاص", "الفلق", "الناس"
    )

    private var data: Map<Int, String>? = null
    private var keys: List<Int> = emptyList()
    private var counts = IntArray(116)
    private var offsets = IntArray(116)

    @Synchronized
    private fun load(ctx: Context) {
        if (data != null) return
        val m = LinkedHashMap<Int, String>(7000)
        ctx.assets.open("quran.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { l ->
                val p = l.split("|", limit = 3)
                if (p.size == 3) m[p[0].toInt() * 1000 + p[1].toInt()] = p[2]
            }
        }
        keys = m.keys.toList()
        val cnt = IntArray(116)
        for (k in keys) cnt[k / 1000]++
        val off = IntArray(116)
        for (i in 2..115) off[i] = off[i - 1] + cnt[i - 1]
        counts = cnt
        offsets = off
        data = m
    }

    // ---- المناسبات (التاريخ الهجري حسب أم القرى، وقد يختلف يوماً عن الرؤية المحلية) ----
    private val FRIDAY = Occ("يوم الجمعة", listOf(r(62, 9, 10), r(33, 56), r(18, 10), r(18, 46), r(18, 110)))
    private val RAMADAN = Occ("شهر رمضان", listOf(r(2, 183), r(2, 185), r(2, 186)))
    private val LAST_TEN = Occ("العشر الأواخر من رمضان", listOf(r(97, 1, 5), r(44, 3, 4)))
    private val EID_FITR = Occ("عيد الفطر", listOf(r(2, 185), r(10, 58), r(87, 14, 15)))
    private val DHUL_HIJJAH = Occ("عشر ذي الحجة", listOf(r(22, 28), r(2, 203), r(22, 37)))
    private val ARAFAH = Occ("يوم عرفة", listOf(r(5, 3), r(2, 198, 199)))
    private val EID_ADHA = Occ("عيد الأضحى", listOf(r(108, 1, 3), r(22, 37), r(2, 203)))
    private val NEW_YEAR = Occ("رأس السنة الهجرية", listOf(r(9, 36), r(2, 218)))
    private val ASHURA = Occ("يوم عاشوراء", listOf(r(26, 63), r(26, 65, 66)))
    private val ISRA = Occ("ذكرى الإسراء والمعراج", listOf(r(17, 1)))

    private fun occasionFor(d: LocalDate): Occ? {
        val h = HijrahDate.from(d)
        val m = h.get(ChronoField.MONTH_OF_YEAR)
        val day = h.get(ChronoField.DAY_OF_MONTH)
        return when {
            m == 10 && day <= 3 -> EID_FITR
            m == 12 && day == 9 -> ARAFAH
            m == 12 && day in 10..13 -> EID_ADHA
            m == 12 && day <= 8 -> DHUL_HIJJAH
            m == 9 && day >= 21 -> LAST_TEN
            m == 9 -> RAMADAN
            m == 1 && day == 1 -> NEW_YEAR
            m == 1 && day == 10 -> ASHURA
            m == 7 && day == 27 -> ISRA
            d.dayOfWeek == DayOfWeek.FRIDAY -> FRIDAY
            else -> null
        }
    }

    private fun pick(mode: Int): Verse {
        val d = data!!
        if (mode == 0) {
            occasionFor(LocalDate.now())?.let { o ->
                val ks = o.refs.flatMap { (s, a, b) -> (a..b).map { s * 1000 + it } }.filter { d.containsKey(it) }
                if (ks.isNotEmpty()) {
                    val k = ks.random()
                    return Verse(k / 1000, k % 1000, d[k]!!, o.title)
                }
            }
        }
        repeat(60) {
            val k = keys.random()
            val t = d[k]!!
            if (t.length <= 230) return Verse(k / 1000, k % 1000, t, null)
        }
        val k = keys.random()
        return Verse(k / 1000, k % 1000, d[k]!!, null)
    }

    /** mode 0 = آية مناسبة لليوم (وإلا متنوعة)، mode 1 = متنوعة من كامل المصحف */
    fun getMode(ctx: Context) = ctx.getSharedPreferences("quran", Context.MODE_PRIVATE).getInt("mode", 0)

    fun setMode(ctx: Context, mode: Int) {
        ctx.getSharedPreferences("quran", Context.MODE_PRIVATE).edit().putInt("mode", mode).apply()
        current(ctx, true)
    }

    /** الآية الحالية؛ تتغير كل 3 ساعات أو عند force */
    fun current(ctx: Context, force: Boolean = false): Verse {
        load(ctx)
        val sp = ctx.getSharedPreferences("quran", Context.MODE_PRIVATE)
        val now = LocalDateTime.now()
        val slot = now.toLocalDate().toEpochDay() * 8 + now.hour / 3
        val saved = sp.getString("cur", null)
        if (!force && saved != null && sp.getLong("slot", -1) == slot) {
            val p = saved.split("|")
            val s = p[0].toInt()
            val a = p[1].toInt()
            data!![s * 1000 + a]?.let { return Verse(s, a, it, p.getOrNull(2)?.ifEmpty { null }) }
        }
        val v = pick(sp.getInt("mode", 0))
        sp.edit().putString("cur", "${v.surah}|${v.ayah}|${v.occasion ?: ""}").putLong("slot", slot).apply()
        return v
    }

    fun ensure(ctx: Context) = load(ctx)

    fun count(ctx: Context, surah: Int): Int { load(ctx); return counts[surah] }

    fun verses(ctx: Context, surah: Int): List<Pair<Int, String>> {
        load(ctx)
        return (1..counts[surah]).map { it to (data!![surah * 1000 + it] ?: "") }
    }

    /** رقم الآية في المصحف كله (1..6236) */
    fun globalNumber(ctx: Context, surah: Int, ayah: Int): Int { load(ctx); return offsets[surah] + ayah }
}
