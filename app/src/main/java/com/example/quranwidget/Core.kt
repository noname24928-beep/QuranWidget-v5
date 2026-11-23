package com.example.quranwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Date
import java.util.Locale

object P {
    val prayerNames = listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")
    fun sp(c: Context) = c.getSharedPreferences("athan", Context.MODE_PRIVATE)

    fun lat(c: Context) = sp(c).getFloat("lat", 30.0444f).toDouble()
    fun lon(c: Context) = sp(c).getFloat("lon", 31.2357f).toDouble()
    fun place(c: Context) = sp(c).getString("place", "القاهرة (افتراضي)") ?: ""
    fun setPlace(c: Context, name: String, lat: Double, lon: Double) {
        sp(c).edit().putString("place", name).putFloat("lat", lat.toFloat()).putFloat("lon", lon.toFloat()).apply()
    }

    fun adhanOn(c: Context, p: Int) = sp(c).getBoolean("adhan_on_$p", true)
    fun setAdhanOn(c: Context, p: Int, v: Boolean) = sp(c).edit().putBoolean("adhan_on_$p", v).apply()
    fun adhanFile(c: Context, p: Int): String? = sp(c).getString("adhan_file_$p", null)
    fun adhanName(c: Context, p: Int): String? = sp(c).getString("adhan_name_$p", null)
    fun setAdhan(c: Context, p: Int, path: String, name: String) =
        sp(c).edit().putString("adhan_file_$p", path).putString("adhan_name_$p", name).apply()

    fun preOn(c: Context) = sp(c).getBoolean("pre_on", false)
    fun setPreOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("pre_on", v).apply()
    fun preMin(c: Context) = sp(c).getInt("pre_min", 10)
    fun setPreMin(c: Context, v: Int) = sp(c).edit().putInt("pre_min", v.coerceIn(1, 60)).apply()
    fun preFile(c: Context): String? = sp(c).getString("pre_file", null)
    fun preName(c: Context): String? = sp(c).getString("pre_name", null)
    fun setPre(c: Context, path: String, name: String) =
        sp(c).edit().putString("pre_file", path).putString("pre_name", name).apply()

    fun cannonOn(c: Context) = sp(c).getBoolean("cannon_on", true)
    fun setCannonOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("cannon_on", v).apply()
    fun suhoorOn(c: Context) = sp(c).getBoolean("suhoor_on", true)
    fun setSuhoorOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("suhoor_on", v).apply()
    fun suhoorMin(c: Context) = sp(c).getInt("suhoor_min", 45)
    fun setSuhoorMin(c: Context, v: Int) = sp(c).edit().putInt("suhoor_min", v.coerceIn(5, 180)).apply()
    fun suhoorFile(c: Context): String? = sp(c).getString("suhoor_file", null)
    fun suhoorName(c: Context): String? = sp(c).getString("suhoor_name", null)
    fun setSuhoor(c: Context, path: String, name: String) =
        sp(c).edit().putString("suhoor_file", path).putString("suhoor_name", name).apply()

    fun hijriOffset(c: Context) = sp(c).getInt("hijri_offset", 0)
    fun setHijriOffset(c: Context, v: Int) = sp(c).edit().putInt("hijri_offset", v.coerceIn(-2, 2)).apply()
}

object Dates {
    val hMonths = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )
    val gMonths = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )
    private val dayNames = mapOf(
        DayOfWeek.SATURDAY to "السبت", DayOfWeek.SUNDAY to "الأحد", DayOfWeek.MONDAY to "الاثنين",
        DayOfWeek.TUESDAY to "الثلاثاء", DayOfWeek.WEDNESDAY to "الأربعاء",
        DayOfWeek.THURSDAY to "الخميس", DayOfWeek.FRIDAY to "الجمعة"
    )

    fun dayName(d: LocalDate) = dayNames[d.dayOfWeek] ?: ""
    fun hijri(c: Context, d: LocalDate): HijrahDate = HijrahDate.from(d.plusDays(P.hijriOffset(c).toLong()))
    fun hMonth(c: Context, d: LocalDate) = hijri(c, d).get(ChronoField.MONTH_OF_YEAR)
    fun hDay(c: Context, d: LocalDate) = hijri(c, d).get(ChronoField.DAY_OF_MONTH)
    fun hYear(c: Context, d: LocalDate) = hijri(c, d).get(ChronoField.YEAR)

    fun line(c: Context, d: LocalDate): String =
        "${dayName(d)} ${hDay(c, d)} ${hMonths[hMonth(c, d) - 1]} ${hYear(c, d)}هـ\n" +
            "${d.dayOfMonth} ${gMonths[d.monthValue - 1]} ${d.year}م"

    fun fmt(ms: Long, ampm: Boolean = true): String {
        val s = SimpleDateFormat(if (ampm) "hh:mm a" else "hh:mm", Locale.US).format(Date(ms))
        return s.replace("AM", "ص").replace("PM", "م")
    }
}

object Times {
    /** [فجر، شروق، ظهر، عصر، مغرب، عشاء] بالميلي ثانية */
    fun day(c: Context, d: LocalDate): LongArray {
        val params = CalculationMethod.EGYPTIAN.parameters
        val pt = PrayerTimes(
            Coordinates(P.lat(c), P.lon(c)),
            DateComponents(d.year, d.monthValue, d.dayOfMonth),
            params
        )
        return longArrayOf(pt.fajr.time, pt.sunrise.time, pt.dhuhr.time, pt.asr.time, pt.maghrib.time, pt.isha.time)
    }

    /** مواضع الصلوات الخمس داخل المصفوفة */
    val IDX = intArrayOf(0, 2, 3, 4, 5)
}

object Scheduler {
    private const val KIND_ADHAN = 0
    private const val KIND_PRE = 1
    private const val KIND_SUHOOR = 2

    fun scheduleAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val today = LocalDate.now()

        for (p in 0..4) {
            // الأذان
            var at = -1L
            if (P.adhanOn(ctx, p)) {
                at = nextTime(now, today) { d -> Times.day(ctx, d)[Times.IDX[p]] }
            }
            set(ctx, am, KIND_ADHAN, p, at)

            // قبل الأذان
            var pt = -1L
            if (P.adhanOn(ctx, p) && P.preOn(ctx) && P.preFile(ctx) != null) {
                val ms = P.preMin(ctx) * 60_000L
                pt = nextTime(now, today) { d -> Times.day(ctx, d)[Times.IDX[p]] - ms }
            }
            set(ctx, am, KIND_PRE, p, pt)
        }

        // المسحراتي (في رمضان فقط)
        var st = -1L
        if (P.suhoorOn(ctx)) {
            val ms = P.suhoorMin(ctx) * 60_000L
            for (i in 0..35) {
                val d = today.plusDays(i.toLong())
                if (Dates.hMonth(ctx, d) != 9) continue
                val t = Times.day(ctx, d)[0] - ms
                if (t > now + 60_000L) { st = t; break }
            }
        }
        set(ctx, am, KIND_SUHOOR, 0, st)

        // الأذكار التلقائية
        val m30 = 30 * 60_000L
        set(ctx, am, 5, 0, if (P3.morningOn(ctx)) nextTime(now, today) { d -> Times.day(ctx, d)[0] + m30 } else -1L)
        set(ctx, am, 6, 0, if (P3.eveningOn(ctx)) nextTime(now, today) { d -> Times.day(ctx, d)[3] + m30 } else -1L)
        set(ctx, am, 7, 0, if (P3.sleepOn(ctx)) nextTime(now, today) { d -> Times.day(ctx, d)[5] + 2 * m30 } else -1L)
        periodic(ctx, am, 8, P3.dhikrOn(ctx), P3.dhikrMin(ctx), "adhkar")
        periodic(ctx, am, 9, P3.salOn(ctx), P3.salMin(ctx), "salawat")
    }

    private fun periodic(ctx: Context, am: AlarmManager, kind: Int, on: Boolean, minutes: Int, key: String) {
        val sp = P.sp(ctx)
        if (!on) {
            set(ctx, am, kind, 0, -1L)
            sp.edit().remove("next_$key").apply()
            return
        }
        val now = System.currentTimeMillis()
        val gap = minutes * 60_000L
        var nxt = sp.getLong("next_$key", 0L)
        if (nxt <= now + 5_000L || nxt > now + gap + 5_000L) {
            nxt = now + gap
            sp.edit().putLong("next_$key", nxt).apply()
        }
        set(ctx, am, kind, 0, nxt)
    }

    private fun nextTime(now: Long, today: LocalDate, f: (LocalDate) -> Long): Long {
        for (i in 0..3) {
            val t = f(today.plusDays(i.toLong()))
            if (t > now + 60_000L) return t
        }
        return -1L
    }

    private fun set(ctx: Context, am: AlarmManager, kind: Int, p: Int, at: Long) {
        val i = Intent(ctx, AlarmReceiver::class.java).putExtra("kind", kind).putExtra("prayer", p)
        val pi = PendingIntent.getBroadcast(
            ctx, kind * 10 + p, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (at < 0) {
            am.cancel(pi)
            return
        }
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}
