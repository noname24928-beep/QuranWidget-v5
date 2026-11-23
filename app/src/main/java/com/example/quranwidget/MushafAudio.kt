package com.example.quranwidget

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** حالة التشغيل المشتركة بين الخدمة والشاشة */
object MushafPlayer {
    var surah = 1
    var ayah = 1
    var playing = false
    var active = false
    var onChange: ((Int, Int, Boolean) -> Unit)? = null
    private val h = Handler(Looper.getMainLooper())

    fun notifyChange() { h.post { onChange?.invoke(surah, ayah, playing) } }

    fun stopped() { active = false; playing = false; notifyChange() }
}

object MushafAudio {
    val defaults = listOf(
        "ar.alafasy" to "مشاري العفاسي",
        "ar.abdulbasitmurattal" to "عبد الباسط عبد الصمد (مرتل)",
        "ar.husary" to "محمود خليل الحصري",
        "ar.minshawi" to "محمد صديق المنشاوي",
        "ar.shaatree" to "أبو بكر الشاطري",
        "ar.mahermuaiqly" to "ماهر المعيقلي",
        "ar.hudhaify" to "علي الحذيفي",
        "ar.ahmedajamy" to "أحمد بن علي العجمي",
        "ar.muhammadayyoub" to "محمد أيوب",
        "ar.abdurrahmaansudais" to "عبد الرحمن السديس",
        "ar.saoodshuraym" to "سعود الشريم"
    )
    val bitrates = listOf(128, 64, 192, 48, 40, 32)

    fun url(rec: String, br: Int, g: Int) = "https://cdn.islamic.network/quran/audio/$br/$rec/$g.mp3"

    fun local(ctx: Context, rec: String, g: Int): File =
        File(File(ctx.filesDir, "mushaf/$rec").apply { mkdirs() }, "$g.mp3")

    fun bitrate(ctx: Context, rec: String) = P.sp(ctx).getInt("br_$rec", 128)
    fun setBitrate(ctx: Context, rec: String, br: Int) = P.sp(ctx).edit().putInt("br_$rec", br).apply()
    fun order(ctx: Context, rec: String): List<Int> = listOf(bitrate(ctx, rec)) + bitrates.filter { it != bitrate(ctx, rec) }

    fun reciter(ctx: Context) = P.sp(ctx).getString("mreciter", "ar.alafasy") ?: "ar.alafasy"
    fun reciterName(ctx: Context) = P.sp(ctx).getString("mreciter_name", "مشاري العفاسي") ?: ""
    fun setReciter(ctx: Context, id: String, name: String) =
        P.sp(ctx).edit().putString("mreciter", id).putString("mreciter_name", name).apply()

    fun reciters(ctx: Context): List<Pair<String, String>> {
        try {
            val arr = JSONArray(P.sp(ctx).getString("mreciters", "[]"))
            if (arr.length() > 0) return (0 until arr.length()).map {
                val o = arr.getJSONObject(it); o.getString("id") to o.getString("name")
            }
        } catch (e: Exception) { }
        return defaults
    }

    fun refreshReciters(act: Activity, done: (Boolean) -> Unit) {
        Thread {
            var ok = false
            try {
                val txt = URL("https://api.alquran.cloud/v1/edition?format=audio&language=ar&type=versebyverse").readText()
                val data = JSONObject(txt).getJSONArray("data")
                val out = JSONArray()
                for (i in 0 until data.length()) {
                    val o = data.getJSONObject(i)
                    out.put(JSONObject().put("id", o.getString("identifier")).put("name", o.getString("name")))
                }
                if (out.length() > 0) {
                    P.sp(act).edit().putString("mreciters", out.toString()).apply()
                    ok = true
                }
            } catch (e: Exception) { }
            act.runOnUiThread { done(ok) }
        }.start()
    }

    fun downloaded(ctx: Context, rec: String, s: Int): Boolean {
        Quran.ensure(ctx)
        val n = Quran.count(ctx, s)
        for (a in 1..n) {
            val f = local(ctx, rec, Quran.globalNumber(ctx, s, a))
            if (!f.exists() || f.length() < 1000) return false
        }
        return true
    }

    fun downloadPairs(act: Activity, rec: String, pairs: List<Pair<Int, Int>>, onProgress: (Int, Int) -> Unit, onDone: (Boolean) -> Unit) {
        Thread {
            var ok = true
            try {
                Quran.ensure(act)
                val total = pairs.size
                for ((idx, pr) in pairs.withIndex()) {
                    val g = Quran.globalNumber(act, pr.first, pr.second)
                    val f = local(act, rec, g)
                    if (!(f.exists() && f.length() > 1000)) {
                        var got = false
                        for (br in order(act, rec)) {
                            try {
                                val c = URL(url(rec, br, g)).openConnection() as HttpURLConnection
                                c.connectTimeout = 15000
                                c.readTimeout = 30000
                                if (c.responseCode == 200) {
                                    val tmp = File(f.path + ".part")
                                    c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                                    if (tmp.length() > 1000 && tmp.renameTo(f)) { setBitrate(act, rec, br); got = true }
                                }
                                c.disconnect()
                            } catch (e: Exception) { }
                            if (got) break
                        }
                        if (!got) { ok = false; break }
                    }
                    act.runOnUiThread { onProgress(idx + 1, total) }
                }
            } catch (e: Exception) { ok = false }
            act.runOnUiThread { onDone(ok) }
        }.start()
    }

    fun download(act: Activity, rec: String, s: Int, onProgress: (Int, Int) -> Unit, onDone: (Boolean) -> Unit) {
        Thread {
            var ok = true
            try {
                Quran.ensure(act)
                val n = Quran.count(act, s)
                for (a in 1..n) {
                    val g = Quran.globalNumber(act, s, a)
                    val f = local(act, rec, g)
                    if (!(f.exists() && f.length() > 1000)) {
                        var got = false
                        for (br in order(act, rec)) {
                            try {
                                val c = URL(url(rec, br, g)).openConnection() as HttpURLConnection
                                c.connectTimeout = 15000
                                c.readTimeout = 30000
                                if (c.responseCode == 200) {
                                    val tmp = File(f.path + ".part")
                                    c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                                    if (tmp.length() > 1000 && tmp.renameTo(f)) {
                                        setBitrate(act, rec, br)
                                        got = true
                                    }
                                }
                                c.disconnect()
                            } catch (e: Exception) { }
                            if (got) break
                        }
                        if (!got) { ok = false; break }
                    }
                    act.runOnUiThread { onProgress(a, n) }
                }
            } catch (e: Exception) { ok = false }
            act.runOnUiThread { onDone(ok) }
        }.start()
    }
}

class MushafService : Service() {
    companion object {
        fun play(c: Context, s: Int, a: Int, rec: String) {
            c.startForegroundService(
                Intent(c, MushafService::class.java).setAction("PLAY")
                    .putExtra("s", s).putExtra("a", a).putExtra("rec", rec)
            )
        }

        fun playQueue(c: Context, spec: String, label: String, rec: String) {
            c.startForegroundService(
                Intent(c, MushafService::class.java).setAction("PLAY")
                    .putExtra("plist", spec).putExtra("label", label).putExtra("rec", rec)
            )
        }

        fun action(c: Context, act: String) {
            c.startService(Intent(c, MushafService::class.java).setAction(act))
        }
    }

    private var mp: MediaPlayer? = null
    private var rec = "ar.alafasy"
    private var attempt = 0
    private var curBr = 128
    private var streaming = false
    private var queue: List<Pair<Int, Int>>? = null
    private var qi = 0
    private var label = ""
    private val h = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY" -> {
                try { MediaService.stop(this) } catch (e: Exception) { }
                rec = intent?.getStringExtra("rec") ?: "ar.alafasy"
                val spec = intent?.getStringExtra("plist")
                label = intent?.getStringExtra("label") ?: ""
                if (spec != null) {
                    val q = Library.parse(this, spec)
                    if (q.isEmpty()) { finish(); return START_NOT_STICKY }
                    queue = q
                    qi = 0
                    MushafPlayer.surah = q[0].first
                    MushafPlayer.ayah = q[0].second
                } else {
                    queue = null
                    MushafPlayer.surah = intent?.getIntExtra("s", 1) ?: 1
                    MushafPlayer.ayah = intent?.getIntExtra("a", 1) ?: 1
                }
                attempt = 0
                MushafPlayer.active = true
                goForeground()
                playCurrent()
            }
            "PAUSE" -> {
                try { mp?.pause() } catch (e: Exception) { }
                MushafPlayer.playing = false
                MushafPlayer.notifyChange()
            }
            "RESUME" -> {
                val m = mp
                if (m == null) { finish(); return START_NOT_STICKY }
                try { m.start() } catch (e: Exception) { }
                MushafPlayer.playing = true
                MushafPlayer.notifyChange()
            }
            "NEXT" -> move(1)
            "PREV" -> move(-1)
            else -> finish()
        }
        return START_NOT_STICKY
    }

    private fun move(d: Int) {
        if (mp == null) { finish(); return }
        val q = queue
        if (q != null) {
            val n = qi + d
            if (n in q.indices) {
                qi = n
                MushafPlayer.surah = q[n].first
                MushafPlayer.ayah = q[n].second
                attempt = 0; goForeground(); playCurrent()
            }
            return
        }
        val n = Quran.count(this, MushafPlayer.surah)
        val a = MushafPlayer.ayah + d
        if (a in 1..n) { MushafPlayer.ayah = a; attempt = 0; goForeground(); playCurrent() }
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("mushaf", "تلاوة المصحف", NotificationManager.IMPORTANCE_LOW))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val stop = PendingIntent.getService(this, 21, Intent(this, MushafService::class.java).setAction("STOP"), flags)
        val open = PendingIntent.getActivity(this, 22, Intent(this, MainActivity::class.java), flags)
        val n = Notification.Builder(this, "mushaf")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(
                (if (label.isNotEmpty()) "$label — " else "") +
                    "سورة ${Quran.names[MushafPlayer.surah - 1]} : ${MushafPlayer.ayah}"
            )
            .setContentText(MushafAudio.reciterName(this))
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(2, n)
    }

    private fun playCurrent() {
        val s = MushafPlayer.surah
        val a = MushafPlayer.ayah
        val g = Quran.globalNumber(this, s, a)
        val f = MushafAudio.local(this, rec, g)
        val order = MushafAudio.order(this, rec)
        curBr = order[attempt.coerceAtMost(order.size - 1)]
        try {
            try { mp?.release() } catch (e: Exception) { }
            val m = MediaPlayer()
            mp = m
            m.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            if (f.exists() && f.length() > 1000) { streaming = false; m.setDataSource(f.path) }
            else { streaming = true; m.setDataSource(MushafAudio.url(rec, curBr, g)) }
            m.setOnPreparedListener {
                if (streaming) MushafAudio.setBitrate(this, rec, curBr)
                it.start()
                MushafPlayer.playing = true
                MushafPlayer.notifyChange()
            }
            m.setOnCompletionListener { onAyahDone() }
            m.setOnErrorListener { _, _, _ -> onError(); true }
            m.prepareAsync()
        } catch (e: Exception) { onError() }
    }

    private fun onError() {
        val order = MushafAudio.order(this, rec)
        if (streaming && attempt < order.size - 1) { attempt++; playCurrent(); return }
        h.post { Toast.makeText(this, "تعذر تشغيل التلاوة. تأكد من الإنترنت أو حمّل السورة.", Toast.LENGTH_LONG).show() }
        finish()
    }

    private fun onAyahDone() {
        val q = queue
        if (q != null) {
            qi++
            if (qi < q.size) {
                MushafPlayer.surah = q[qi].first
                MushafPlayer.ayah = q[qi].second
                goForeground()
                playCurrent()
            } else finish()
            return
        }
        val n = Quran.count(this, MushafPlayer.surah)
        if (MushafPlayer.ayah < n) {
            MushafPlayer.ayah++
            goForeground()
            playCurrent()
        } else if (P.sp(this).getBoolean("mnext", false) && MushafPlayer.surah < 114) {
            MushafPlayer.surah++
            MushafPlayer.ayah = 1
            goForeground()
            playCurrent()
        } else finish()
    }

    private fun finish() {
        try { mp?.release() } catch (e: Exception) { }
        mp = null
        MushafPlayer.stopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try { mp?.release() } catch (e: Exception) { }
        mp = null
        super.onDestroy()
    }
}
