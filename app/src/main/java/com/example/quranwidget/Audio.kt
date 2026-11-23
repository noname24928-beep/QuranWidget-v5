package com.example.quranwidget

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale

data class Item(val name: String, val url: String, val kind: String)

object Catalog {
    fun dir(c: Context): File = File(c.filesDir, "audio").apply { mkdirs() }

    private fun parse(arr: JSONArray?, kind: String): List<Item> {
        val out = ArrayList<Item>()
        if (arr == null) return out
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Item(o.getString("name"), o.getString("url"), kind))
        }
        return out
    }

    private fun builtin(c: Context, kind: String): List<Item> = try {
        val txt = c.assets.open("catalog.json").bufferedReader(Charsets.UTF_8).readText()
        parse(JSONObject(txt).optJSONArray(kind), kind)
    } catch (e: Exception) { emptyList() }

    private fun custom(c: Context, kind: String): List<Item> = try {
        val arr = JSONArray(P.sp(c).getString("custom_$kind", "[]"))
        parse(arr, kind)
    } catch (e: Exception) { emptyList() }

    fun all(c: Context, kind: String): List<Item> = builtin(c, kind) + custom(c, kind)

    fun addCustom(c: Context, item: Item) {
        val arr = try { JSONArray(P.sp(c).getString("custom_${item.kind}", "[]")) } catch (e: Exception) { JSONArray() }
        arr.put(JSONObject().put("name", item.name).put("url", item.url))
        P.sp(c).edit().putString("custom_${item.kind}", arr.toString()).apply()
    }

    fun file(c: Context, item: Item): File =
        if (item.url.startsWith("local:")) File(dir(c), item.url.removePrefix("local:"))
        else File(dir(c), "u" + item.url.hashCode().toString().replace("-", "m") + ".mp3")
}

object Downloader {
    private fun open(url: String): HttpURLConnection {
        var u = url
        var n = 0
        while (true) {
            val conn = URL(u).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = false
            val code = conn.responseCode
            if (code in 301..308 && n < 6) {
                val loc = conn.getHeaderField("Location") ?: break
                u = URL(URL(u), loc).toString()
                n++
                conn.disconnect()
                continue
            }
            return conn
        }
        throw Exception("redirect")
    }

    fun download(act: Activity, item: Item, onProgress: (Int) -> Unit, onDone: (File?) -> Unit) {
        Thread {
            var ok: File? = null
            try {
                val out = Catalog.file(act, item)
                val tmp = File(out.path + ".part")
                val conn = open(item.url)
                if (conn.responseCode !in 200..299) throw Exception("http ${conn.responseCode}")
                val total = conn.contentLength
                conn.inputStream.use { ins ->
                    tmp.outputStream().use { os ->
                        val b = ByteArray(16384)
                        var read = 0L
                        var last = -1
                        while (true) {
                            val n = ins.read(b)
                            if (n < 0) break
                            os.write(b, 0, n)
                            read += n
                            if (total > 0) {
                                val pct = (read * 100 / total).toInt()
                                if (pct != last) { last = pct; act.runOnUiThread { onProgress(pct) } }
                            }
                        }
                    }
                }
                if (tmp.length() < 1000) throw Exception("too small")
                out.delete()
                if (!tmp.renameTo(out)) throw Exception("rename")
                ok = out
            } catch (e: Exception) {
                ok = null
            }
            act.runOnUiThread { onDone(ok) }
        }.start()
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val kind = i.getIntExtra("kind", 0)
        val p = i.getIntExtra("prayer", 0)
        try {
            when (kind) {
                0, 1, 2 -> AthanService.start(c, kind, p, null)
                5, 6, 7 -> AdhkarNotifier.post(c, kind)
                8 -> {
                    P.sp(c).edit().putLong("next_adhkar", System.currentTimeMillis() + P3.dhikrMin(c) * 60_000L).apply()
                    if (P3.inHours(5, 23)) AdhkarNotifier.post(c, 8)
                }
                9 -> {
                    P.sp(c).edit().putLong("next_salawat", System.currentTimeMillis() + P3.salMin(c) * 60_000L).apply()
                    if (P3.inHours(P3.salStart(c), P3.salEnd(c))) SalawatService.start(c)
                }
            }
        } catch (e: Exception) { }
        try { Scheduler.scheduleAll(c) } catch (e: Exception) { }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        try { Scheduler.scheduleAll(c) } catch (e: Exception) { }
    }
}

class AthanService : Service() {
    companion object {
        const val ACTION_PLAY = "PLAY"
        const val ACTION_STOP = "STOP"
        const val KIND_ADHAN = 0
        const val KIND_PRE = 1
        const val KIND_SUHOOR = 2
        const val KIND_TEST = 3
        const val KIND_CANNON = 4

        @Volatile var busy = false

        fun start(c: Context, kind: Int, prayer: Int = 0, path: String? = null) {
            val i = Intent(c, AthanService::class.java).setAction(ACTION_PLAY)
                .putExtra("kind", kind).putExtra("prayer", prayer).putExtra("path", path)
            c.startForegroundService(i)
        }

        fun stop(c: Context) {
            c.startService(Intent(c, AthanService::class.java).setAction(ACTION_STOP))
        }
    }

    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val kind = intent?.getIntExtra("kind", 0) ?: 0
        val p = (intent?.getIntExtra("prayer", 0) ?: 0).coerceIn(0, 4)
        val path = intent?.getStringExtra("path")
        val title = when (kind) {
            KIND_ADHAN -> "حان الآن موعد أذان ${P.prayerNames[p]}"
            KIND_PRE -> "بعد قليل أذان ${P.prayerNames[p]}"
            KIND_SUHOOR -> "وقت السحور"
            KIND_CANNON -> "مدفع الإفطار"
            else -> "تجربة الصوت"
        }
        try { stopService(Intent(this, MushafService::class.java)); MushafPlayer.stopped() } catch (e: Exception) { }
        try { SalawatService.stop(this) } catch (e: Exception) { }
        try { MediaService.stop(this); } catch (e: Exception) { }
        busy = true
        goForeground(title)
        stopAll()
        try {
            when (kind) {
                KIND_ADHAN -> {
                    val file = P.adhanFile(this, p)
                    val ramadan = Dates.hMonth(this, LocalDate.now()) == 9
                    if (p == 3 && ramadan && P.cannonOn(this)) {
                        cannon { playFile(file, false, true, true) }
                    } else playFile(file, false, true, true)
                }
                KIND_PRE -> {
                    val f = P.preFile(this)
                    if (f == null || !File(f).exists()) { stopSelf(); return START_NOT_STICKY }
                    playFile(f, true, false, false)
                    handler.postDelayed({ stopSelf() }, (P.preMin(this) + 1) * 60_000L)
                }
                KIND_SUHOOR -> {
                    val f = P.suhoorFile(this)
                    if (f != null && File(f).exists()) playFile(f, false, false, false) else speakMusaharati()
                }
                KIND_CANNON -> cannon { stopSelf() }
                else -> playFile(path, false, true, false)
            }
        } catch (e: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun goForeground(title: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("athan", "الأذان والتنبيهات", NotificationManager.IMPORTANCE_LOW)
        )
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val stop = PendingIntent.getService(
            this, 9, Intent(this, AthanService::class.java).setAction(ACTION_STOP), flags
        )
        val open = PendingIntent.getActivity(this, 8, Intent(this, MainActivity::class.java), flags)
        val n = Notification.Builder(this, "athan")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(1, n)
    }

    private fun playFile(path: String?, loop: Boolean, alarmStream: Boolean, fallbackRingtone: Boolean) {
        val mp = MediaPlayer()
        player = mp
        val usage = if (alarmStream) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA
        mp.setAudioAttributes(
            AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        )
        if (path != null && File(path).exists()) {
            mp.setDataSource(path)
        } else if (fallbackRingtone) {
            mp.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        } else {
            stopSelf(); return
        }
        mp.isLooping = loop
        mp.setOnCompletionListener { if (!loop) stopSelf() }
        mp.setOnErrorListener { _, _, _ -> stopSelf(); true }
        mp.setOnPreparedListener { it.start() }
        mp.prepareAsync()
    }

    /** صوت مدفع مُولَّد برمجياً (لا يحتاج ملفات) */
    private fun cannon(done: () -> Unit) {
        val custom = P3.cannonFile(this)
        if (custom != null) {
            try {
                val m = MediaPlayer()
                player = m
                m.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                m.setDataSource(custom)
                m.setOnPreparedListener { it.start() }
                m.setOnCompletionListener {
                    try { m.release() } catch (e: Exception) { }
                    if (player === m) player = null
                    done()
                }
                m.setOnErrorListener { _, _, _ -> if (player === m) player = null; done(); true }
                m.prepareAsync()
                return
            } catch (e: Exception) { }
        }
        Thread {
            try {
                val sr = 22050
                val n = sr * 2
                val buf = ShortArray(n)
                val rnd = java.util.Random(7)
                for (i in 0 until n) {
                    val t = i / sr.toDouble()
                    val env = Math.exp(-3.0 * t)
                    val thump = Math.sin(2 * Math.PI * (70.0 - 25.0 * Math.min(t, 1.0)) * t)
                    val noise = (rnd.nextDouble() * 2 - 1) * Math.exp(-9.0 * t)
                    val v = (0.75 * thump * env + 0.55 * noise).coerceIn(-1.0, 1.0)
                    buf[i] = (v * 32767).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                    )
                    .setBufferSizeInBytes(n * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(buf, 0, n)
                track.play()
                Thread.sleep(2300)
                track.release()
            } catch (e: Exception) { }
            handler.post { done() }
        }.start()
    }

    private fun speakMusaharati() {
        handler.postDelayed({ stopSelf() }, 120_000L)
        tts = TextToSpeech(this) { st ->
            val t = tts
            if (st != TextToSpeech.SUCCESS || t == null) { stopSelf(); return@TextToSpeech }
            t.setLanguage(Locale("ar"))
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { if (utteranceId == "s2") handler.post { stopSelf() } }
                @Deprecated("deprecated")
                override fun onError(utteranceId: String?) { handler.post { stopSelf() } }
            })
            val text = "اصحى يا نايم، وحّد الدايم، قوموا إلى السحور يا عباد الله"
            for (i in 0..2) t.speak(text, TextToSpeech.QUEUE_ADD, null, "s$i")
        }
    }

    private fun stopAll() {
        handler.removeCallbacksAndMessages(null)
        try { player?.stop() } catch (e: Exception) { }
        try { player?.release() } catch (e: Exception) { }
        player = null
        try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { }
        tts = null
    }

    override fun onDestroy() {
        busy = false
        stopAll()
        super.onDestroy()
    }
}
