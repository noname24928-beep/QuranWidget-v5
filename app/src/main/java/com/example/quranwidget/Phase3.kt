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
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.Locale

/** إعدادات المرحلة 3 */
object P3 {
    private fun sp(c: Context) = P.sp(c)

    fun morningOn(c: Context) = sp(c).getBoolean("ad_morning", false)
    fun eveningOn(c: Context) = sp(c).getBoolean("ad_evening", false)
    fun sleepOn(c: Context) = sp(c).getBoolean("ad_sleep", false)
    fun dhikrOn(c: Context) = sp(c).getBoolean("ad_periodic", false)
    fun dhikrMin(c: Context) = sp(c).getInt("ad_periodic_min", 60)
    fun put(c: Context, k: String, v: Boolean) = sp(c).edit().putBoolean(k, v).apply()
    fun putInt(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()

    fun salOn(c: Context) = sp(c).getBoolean("sal_on", false)
    fun salMin(c: Context) = sp(c).getInt("sal_min", 30)
    fun salStart(c: Context) = sp(c).getInt("sal_start", 6)
    fun salEnd(c: Context) = sp(c).getInt("sal_end", 23)

    fun salFormulas(c: Context): Set<Int> {
        val s = sp(c).getString("sal_formulas", null) ?: return SalawatService.formulas.indices.toSet()
        return s.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    }
    fun setSalFormulas(c: Context, set: Set<Int>) = sp(c).edit().putString("sal_formulas", set.joinToString(",")).apply()

    fun salFiles(c: Context): List<String> = try {
        val arr = JSONArray(sp(c).getString("sal_files", "[]"))
        (0 until arr.length()).map { arr.getString(it) }.filter { File(it).exists() }
    } catch (e: Exception) { emptyList() }
    fun addSalFile(c: Context, path: String) {
        val arr = JSONArray(salFiles(c))
        arr.put(path)
        sp(c).edit().putString("sal_files", arr.toString()).apply()
    }
    fun clearSalFiles(c: Context) = sp(c).edit().remove("sal_files").apply()

    fun cannonFile(c: Context): String? = sp(c).getString("cannon_file", null)?.takeIf { File(it).exists() }
    fun cannonName(c: Context): String? = sp(c).getString("cannon_name", null)
    fun setCannon(c: Context, path: String, name: String) =
        sp(c).edit().putString("cannon_file", path).putString("cannon_name", name).apply()
    fun clearCannon(c: Context) = sp(c).edit().remove("cannon_file").remove("cannon_name").apply()

    fun inHours(start: Int, end: Int): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (start <= end) h in start until end else (h >= start || h < end)
    }
}

data class Dhikr(val text: String, val count: Int)
data class DhikrCat(val id: Int, val name: String, val audio: String, val items: List<Dhikr>)

object Adhkar {
    const val AUDIO_BASE = "https://raw.githubusercontent.com/rn0x/Adhkar-json/main"
    private var cats: List<DhikrCat>? = null

    @Synchronized
    fun all(c: Context): List<DhikrCat> {
        cats?.let { return it }
        val out = ArrayList<DhikrCat>()
        try {
            val txt = c.assets.open("adhkar.json").bufferedReader(Charsets.UTF_8).readText()
            val arr = JSONArray(txt)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val items = ArrayList<Dhikr>()
                val a = o.optJSONArray("array")
                if (a != null) for (k in 0 until a.length()) {
                    val d = a.getJSONObject(k)
                    val t = d.optString("text").trim()
                    if (t.isNotEmpty()) items.add(Dhikr(t, d.optInt("count", 1)))
                }
                out.add(DhikrCat(o.optInt("id", i), o.optString("category"), o.optString("audio"), items))
            }
        } catch (e: Exception) { }
        cats = out
        return out
    }

    fun byName(c: Context, part: String) = all(c).firstOrNull { it.name.contains(part) }

    fun randomFrom(c: Context, part: String): String? =
        byName(c, part)?.items?.filter { it.text.length < 450 }?.randomOrNull()?.text

    fun randomShort(c: Context): Pair<String, String>? {
        val cat = all(c).filter { it.items.any { d -> d.text.length in 15..220 } }.randomOrNull() ?: return null
        val d = cat.items.filter { it.text.length in 15..220 }.random()
        return cat.name to d.text
    }
}

object AdhkarNotifier {
    fun post(c: Context, kind: Int) {
        val (title, text) = when (kind) {
            5 -> "أذكار الصباح" to (Adhkar.randomFrom(c, "الصباح والمساء") ?: "حان وقت أذكار الصباح")
            6 -> "أذكار المساء" to (Adhkar.randomFrom(c, "الصباح والمساء") ?: "حان وقت أذكار المساء")
            7 -> "أذكار النوم" to (Adhkar.randomFrom(c, "أذكار النوم") ?: "حان وقت أذكار النوم")
            else -> {
                val p = Adhkar.randomShort(c) ?: return
                "ذكر: ${p.first}" to p.second
            }
        }
        show(c, kind, title, text)
    }

    fun show(c: Context, id: Int, title: String, text: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("adhkar", "الأذكار", NotificationManager.IMPORTANCE_DEFAULT))
        val i = Intent(c, MainActivity::class.java).putExtra("tab", 2).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(c, 100 + id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(c, "adhkar")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { nm.notify(100 + id, n) } catch (e: SecurityException) { }
    }
}

class SalawatService : Service() {
    companion object {
        val formulas = listOf(
            "اللهم صلِّ على محمد",
            "اللهم صلِّ وسلِّم وبارك على سيدنا محمد",
            "اللهم صلِّ على محمد وعلى آل محمد، كما صليت على إبراهيم وعلى آل إبراهيم، إنك حميد مجيد. اللهم بارك على محمد وعلى آل محمد، كما باركت على إبراهيم وعلى آل إبراهيم، إنك حميد مجيد",
            "صلى الله عليه وسلم",
            "اللهم صلِّ على محمد النبي الأمي، وعلى آله وسلم تسليماً",
            "اللهم صلِّ على سيدنا محمد وعلى آله وصحبه وسلم",
            "اللهم صلِّ وسلم على نبينا محمد",
            "اللهم صلِّ على محمد عبدك ونبيك ورسولك النبي الأمي، وعلى آله وسلم"
        )

        fun start(c: Context, test: Boolean = false) {
            c.startForegroundService(Intent(c, SalawatService::class.java).setAction("PLAY").putExtra("test", test))
        }

        fun stop(c: Context) { c.stopService(Intent(c, SalawatService::class.java)) }
    }

    private var mp: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private val h = Handler(Looper.getMainLooper())

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val test = intent?.getBooleanExtra("test", false) ?: false
        val chosen = P3.salFormulas(this).toList().ifEmpty { listOf(0) }
        val formula = formulas[chosen.random().coerceIn(0, formulas.size - 1)]
        goForeground(formula)
        if (intent?.action != "PLAY" || (!test && (MushafPlayer.playing || AthanService.busy || LivePlayer.playing))) {
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY
        }
        cleanup()
        val files = P3.salFiles(this)
        if (files.isNotEmpty()) playFile(files.random()) else speak(formula)
        return START_NOT_STICKY
    }

    private fun goForeground(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("salawat", "الصلاة على النبي", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 31, Intent(this, SalawatService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, "salawat")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("صلُّوا على النبي ﷺ")
            .setContentText(text)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(3, n)
    }

    private fun playFile(path: String) {
        try {
            val m = MediaPlayer()
            mp = m
            m.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            m.setDataSource(path)
            m.setOnPreparedListener { it.start() }
            m.setOnCompletionListener { stopSelf() }
            m.setOnErrorListener { _, _, _ -> stopSelf(); true }
            m.prepareAsync()
        } catch (e: Exception) { stopSelf() }
    }

    private fun speak(text: String) {
        h.postDelayed({ stopSelf() }, 90_000L)
        tts = TextToSpeech(this) { st ->
            val t = tts
            if (st != TextToSpeech.SUCCESS || t == null) { stopSelf(); return@TextToSpeech }
            val r = t.setLanguage(Locale("ar"))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                h.post {
                    Toast.makeText(this, "صوت النطق العربي غير مثبت في هاتفك. ثبّته من إعدادات الهاتف أو أضف ملفاً صوتياً.", Toast.LENGTH_LONG).show()
                }
                stopSelf(); return@TextToSpeech
            }
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { h.post { stopSelf() } }
                @Deprecated("deprecated")
                override fun onError(id: String?) { h.post { stopSelf() } }
            })
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sal")
        }
    }

    private fun cleanup() {
        h.removeCallbacksAndMessages(null)
        try { mp?.release() } catch (e: Exception) { }
        mp = null
        try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { }
        tts = null
    }

    override fun onDestroy() { cleanup(); super.onDestroy() }
}
