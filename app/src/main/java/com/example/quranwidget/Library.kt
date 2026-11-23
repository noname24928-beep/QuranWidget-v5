package com.example.quranwidget

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
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

class Entry(val title: String, val spec: String)
class TextItem(val title: String, val text: String, val source: String)

object Library {
    val stories = listOf(
        Entry("قصة آدم عليه السلام", "2:30-39;7:11-25;20:115-123"),
        Entry("قصة إدريس عليه السلام", "19:56-57"),
        Entry("قصة نوح عليه السلام", "11:25-48;71:1-28"),
        Entry("قصة هود عليه السلام", "7:65-72;11:50-60"),
        Entry("قصة صالح عليه السلام", "7:73-79;11:61-68"),
        Entry("قصة إبراهيم عليه السلام", "2:124-129;6:74-83;21:51-70;37:83-113"),
        Entry("قصة لوط عليه السلام", "7:80-84;11:77-83"),
        Entry("قصة يوسف عليه السلام (سورة يوسف)", "12:1-111"),
        Entry("قصة شعيب عليه السلام", "7:85-93;11:84-95"),
        Entry("قصة موسى عليه السلام", "28:3-43;20:9-98"),
        Entry("موسى والعبد الصالح (الكهف)", "18:60-82"),
        Entry("قصة أيوب عليه السلام", "21:83-84;38:41-44"),
        Entry("قصة يونس عليه السلام", "37:139-148;21:87-88;68:48-50"),
        Entry("قصة زكريا ويحيى عليهما السلام", "3:37-41;19:1-15"),
        Entry("قصة مريم وعيسى عليهما السلام", "19:16-36;3:45-59;5:110-118"),
        Entry("قصة داود عليه السلام", "38:17-26;34:10-11;21:78-80"),
        Entry("قصة سليمان عليه السلام", "27:15-44;38:30-40;34:12-14"),
        Entry("قصة إلياس عليه السلام", "37:123-132"),
        Entry("قصة ذي الكفل واليسع", "21:85-86;38:48"),
        Entry("الإسراء والمعراج", "17:1;53:1-18"),
        Entry("أصحاب الكهف", "18:9-26"),
        Entry("ذو القرنين", "18:83-98")
    )

    val signsAyat = listOf(
        Entry("اقتراب الساعة وانشقاق القمر", "54:1-8"),
        Entry("اقتراب أشراط الساعة", "47:18"),
        Entry("طلوع الشمس من مغربها", "6:158"),
        Entry("خروج يأجوج ومأجوج", "18:93-99;21:96-97"),
        Entry("خروج الدابة", "27:82-83"),
        Entry("الدخان", "44:10-16"),
        Entry("نزول عيسى عليه السلام علمٌ للساعة", "43:61"),
        Entry("النفخ في الصور", "39:68-70"),
        Entry("أهوال يوم القيامة (التكوير والانفطار)", "81:1-14;82:1-5"),
        Entry("الزلزلة والقارعة", "99:1-8;101:1-11")
    )

    val ruqyah = listOf(
        Entry("الرقية الشرعية الشاملة", "1:1-7;2:1-5;2:163;2:255-257;2:284-286;3:18-19;7:54-56;7:117-122;10:79-82;20:65-69;23:115-118;37:1-10;55:33-36;59:21-24;72:1-9;109:1-6;112:1-4;113:1-5;114:1-6"),
        Entry("الرقية المختصرة", "1:1-7;2:255;2:285-286;112:1-4;113:1-5;114:1-6"),
        Entry("رقية العين والحسد", "1:1-7;2:255;68:51-52;112:1-4;113:1-5;114:1-6"),
        Entry("رقية السحر (آيات الإبطال)", "7:117-122;10:79-82;20:65-69"),
        Entry("آية الكرسي والمعوذات (للحفظ)", "2:255;112:1-4;113:1-5;114:1-6"),
        Entry("سورة البقرة كاملة", "2:1-286")
    )

    val ruqyahTexts = listOf(
        TextItem("رقية جبريل للنبي ﷺ",
            "بِسْمِ اللَّهِ أَرْقِيكَ، مِنْ كُلِّ شَيْءٍ يُؤْذِيكَ، مِنْ شَرِّ كُلِّ نَفْسٍ أَوْ عَيْنِ حَاسِدٍ، اللَّهُ يَشْفِيكَ، بِسْمِ اللَّهِ أَرْقِيكَ",
            "رواه مسلم"),
        TextItem("دعاء الشفاء",
            "اللَّهُمَّ رَبَّ النَّاسِ، أَذْهِبِ الْبَاسَ، اشْفِهِ وَأَنْتَ الشَّافِي، لَا شِفَاءَ إِلَّا شِفَاؤُكَ، شِفَاءً لَا يُغَادِرُ سَقَمًا",
            "متفق عليه"),
        TextItem("التعوذ من شر ما خلق",
            "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ",
            "رواه مسلم"),
        TextItem("لا يضر مع اسمه شيء (ثلاث مرات)",
            "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ",
            "رواه أبو داود والترمذي"),
        TextItem("عند الشعور بالألم",
            "يضع يده على موضع الألم ويقول: بِسْمِ اللَّهِ (ثلاثاً)، ثم يقول سبع مرات: أَعُوذُ بِاللَّهِ وَقُدْرَتِهِ مِنْ شَرِّ مَا أَجِدُ وَأُحَاذِرُ",
            "رواه مسلم")
    )

    val signsTexts = listOf(
        TextItem("حديث جبريل: أمارات الساعة",
            "قَالَ: فَأَخْبِرْنِي عَنِ السَّاعَةِ. قَالَ: مَا الْمَسْؤُولُ عَنْهَا بِأَعْلَمَ مِنَ السَّائِلِ. قَالَ: فَأَخْبِرْنِي عَنْ أَمَارَاتِهَا. قَالَ: أَنْ تَلِدَ الْأَمَةُ رَبَّتَهَا، وَأَنْ تَرَى الْحُفَاةَ الْعُرَاةَ الْعَالَةَ رِعَاءَ الشَّاءِ يَتَطَاوَلُونَ فِي الْبُنْيَانِ",
            "رواه مسلم"),
        TextItem("الآيات العشر قبل الساعة",
            "إِنَّهَا لَنْ تَقُومَ حَتَّى تَرَوْنَ قَبْلَهَا عَشْرَ آيَاتٍ: فَذَكَرَ الدُّخَانَ، وَالدَّجَّالَ، وَالدَّابَّةَ، وَطُلُوعَ الشَّمْسِ مِنْ مَغْرِبِهَا، وَنُزُولَ عِيسَى ابْنِ مَرْيَمَ، وَيَأْجُوجَ وَمَأْجُوجَ، وَثَلَاثَةَ خُسُوفٍ: خَسْفٌ بِالْمَشْرِقِ، وَخَسْفٌ بِالْمَغْرِبِ، وَخَسْفٌ بِجَزِيرَةِ الْعَرَبِ، وَآخِرُ ذَلِكَ نَارٌ تَخْرُجُ مِنَ الْيَمَنِ تَطْرُدُ النَّاسَ إِلَى مَحْشَرِهِمْ",
            "رواه مسلم"),
        TextItem("بعثت أنا والساعة كهاتين",
            "بُعِثْتُ أَنَا وَالسَّاعَةُ كَهَاتَيْنِ، وَقَرَنَ بَيْنَ إِصْبَعَيْهِ السَّبَّابَةِ وَالْوُسْطَى",
            "متفق عليه"),
        TextItem("قبض العلم وكثرة الهرج",
            "لَا تَقُومُ السَّاعَةُ حَتَّى يُقْبَضَ الْعِلْمُ، وَتَكْثُرَ الزَّلَازِلُ، وَيَتَقَارَبَ الزَّمَانُ، وَتَظْهَرَ الْفِتَنُ، وَيَكْثُرَ الْهَرْجُ، وَهُوَ الْقَتْلُ، حَتَّى يَكْثُرَ فِيكُمُ الْمَالُ فَيَفِيضَ",
            "رواه البخاري"),
        TextItem("طلوع الشمس من مغربها",
            "لَا تَقُومُ السَّاعَةُ حَتَّى تَطْلُعَ الشَّمْسُ مِنْ مَغْرِبِهَا، فَإِذَا طَلَعَتْ وَرَآهَا النَّاسُ آمَنُوا أَجْمَعُونَ، وَذَلِكَ حِينَ لَا يَنْفَعُ نَفْسًا إِيمَانُهَا لَمْ تَكُنْ آمَنَتْ مِنْ قَبْلُ",
            "متفق عليه"),
        TextItem("إضاعة الأمانة",
            "إِذَا ضُيِّعَتِ الْأَمَانَةُ فَانْتَظِرِ السَّاعَةَ. قَالَ: كَيْفَ إِضَاعَتُهَا؟ قَالَ: إِذَا وُسِّدَ الْأَمْرُ إِلَى غَيْرِ أَهْلِهِ فَانْتَظِرِ السَّاعَةَ",
            "رواه البخاري")
    )

    /** يحوّل "2:30-39;7:11-25" إلى قائمة (سورة، آية) مع ضبط الحدود */
    fun parse(ctx: Context, spec: String): List<Pair<Int, Int>> {
        Quran.ensure(ctx)
        val out = ArrayList<Pair<Int, Int>>()
        for (part in spec.split(";")) {
            val sp = part.trim().split(":")
            if (sp.size != 2) continue
            val s = sp[0].toIntOrNull() ?: continue
            if (s !in 1..114) continue
            val n = Quran.count(ctx, s)
            val r = sp[1].split("-")
            val a = (r[0].toIntOrNull() ?: continue).coerceIn(1, n)
            val b = (if (r.size > 1) r[1].toIntOrNull() ?: a else a).coerceIn(a, n)
            for (i in a..b) out.add(s to i)
        }
        return out
    }
}

/** نطق النصوص بالصوت العربي المثبت في الهاتف */
object Speaker {
    private var tts: TextToSpeech? = null

    fun speak(ctx: Context, text: String, onFail: () -> Unit) {
        stop()
        tts = TextToSpeech(ctx.applicationContext) { st ->
            val t = tts
            if (st != TextToSpeech.SUCCESS || t == null) { onFail(); return@TextToSpeech }
            val r = t.setLanguage(Locale("ar"))
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) { onFail(); return@TextToSpeech }
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lib")
        }
    }

    fun stop() {
        try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { }
        tts = null
    }
}

/** حالة التشغيل المباشر (الإذاعة والمقاطع) */
object LivePlayer {
    var title = ""
    var playing = false
    var onChange: (() -> Unit)? = null
    private val h = Handler(Looper.getMainLooper())
    fun changed() { h.post { onChange?.invoke() } }
}

class MediaService : Service() {
    companion object {
        fun play(c: Context, src: String, title: String) {
            c.startForegroundService(
                Intent(c, MediaService::class.java).setAction("PLAY").putExtra("src", src).putExtra("title", title)
            )
        }
        fun stop(c: Context) { c.stopService(Intent(c, MediaService::class.java)) }
    }

    private var mp: MediaPlayer? = null
    private val h = Handler(Looper.getMainLooper())

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val src = intent?.getStringExtra("src")
        val title = intent?.getStringExtra("title") ?: ""
        goForeground(if (title.isEmpty()) "تشغيل" else title)
        if (intent?.action != "PLAY" || src == null) { finish(); return START_NOT_STICKY }
        try { stopService(Intent(this, MushafService::class.java)); MushafPlayer.stopped() } catch (e: Exception) { }
        try { SalawatService.stop(this) } catch (e: Exception) { }
        LivePlayer.title = title
        LivePlayer.playing = true
        LivePlayer.changed()
        try {
            try { mp?.release() } catch (e: Exception) { }
            val m = MediaPlayer()
            mp = m
            m.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            m.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            m.setDataSource(src)
            m.setOnPreparedListener { it.start() }
            m.setOnCompletionListener { finish() }
            m.setOnErrorListener { _, _, _ ->
                h.post { Toast.makeText(this, "تعذر التشغيل. تأكد من الإنترنت أو الرابط.", Toast.LENGTH_LONG).show() }
                finish(); true
            }
            m.prepareAsync()
        } catch (e: Exception) { finish() }
        return START_NOT_STICKY
    }

    private fun goForeground(title: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("media", "المكتبة الصوتية", NotificationManager.IMPORTANCE_LOW))
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val stop = PendingIntent.getService(this, 41, Intent(this, MediaService::class.java).setAction("STOP"), flags)
        val open = PendingIntent.getActivity(this, 42, Intent(this, MainActivity::class.java).putExtra("tab", 3), flags)
        val n = Notification.Builder(this, "media")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(4, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(4, n)
    }

    private fun finish() {
        try { mp?.release() } catch (e: Exception) { }
        mp = null
        LivePlayer.playing = false
        LivePlayer.changed()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try { mp?.release() } catch (e: Exception) { }
        mp = null
        LivePlayer.playing = false
        LivePlayer.changed()
        super.onDestroy()
    }
}
