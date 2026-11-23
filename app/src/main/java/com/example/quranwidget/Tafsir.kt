package com.example.quranwidget

import android.app.Activity
import android.content.Context
import org.json.JSONArray
import java.io.File
import java.net.URL

object Tafsir {
    val options = listOf(
        "muyassar" to "التفسير الميسر",
        "saadi" to "تفسير السعدي",
        "jalalayn" to "تفسير الجلالين",
        "ibnkathir" to "تفسير ابن كثير (يُحمّل أول مرة لكل سورة)"
    )
    private val cache = HashMap<String, Map<Int, String>>()

    fun name(slug: String) = options.firstOrNull { it.first == slug }?.second ?: ""

    fun fetch(act: Activity, slug: String, s: Int, a: Int, cb: (String) -> Unit) {
        Thread {
            val text = try { load(act, slug, s, a) } catch (e: Exception) { null }
            act.runOnUiThread { cb(text ?: "تعذر تحميل التفسير. تأكد من الاتصال بالإنترنت.") }
        }.start()
    }

    private fun load(ctx: Context, slug: String, s: Int, a: Int): String? {
        if (slug == "ibnkathir") return ibn(ctx, s, a)
        val m = map(ctx, slug)
        var i = a
        while (i >= 1 && a - i < 60) {
            val t = m[s * 1000 + i]
            if (t != null) return if (i == a) t else "(تفسير هذه الآية مذكور مع الآية $i)\n\n$t"
            i--
        }
        return null
    }

    private fun unescape(s: String): String {
        if (!s.contains('\\')) return s
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val n = s[i + 1]
                sb.append(if (n == 'n') '\n' else n)
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    private fun map(ctx: Context, slug: String): Map<Int, String> = synchronized(cache) {
        cache.getOrPut(slug) {
            val m = HashMap<Int, String>(7000)
            ctx.assets.open("tafsir_$slug.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { l ->
                    val p = l.split("|", limit = 3)
                    if (p.size == 3) m[p[0].toInt() * 1000 + p[1].toInt()] = unescape(p[2])
                }
            }
            m
        }
    }

    private fun ibn(ctx: Context, s: Int, a: Int): String? {
        val dir = File(ctx.filesDir, "tafsir").apply { mkdirs() }
        val f = File(dir, "ibnkathir_$s.json")
        if (!f.exists() || f.length() < 100) {
            val u = "https://raw.githubusercontent.com/spa5k/tafsir_api/main/tafsir/ar-tafsir-ibn-kathir/$s.json"
            val c = URL(u).openConnection()
            c.connectTimeout = 15000
            c.readTimeout = 60000
            val bytes = c.getInputStream().readBytes()
            if (bytes.size < 100) return null
            f.writeBytes(bytes)
        }
        val arr = JSONArray(f.readText(Charsets.UTF_8))
        var i = a
        while (i >= 1 && a - i < 60) {
            for (k in 0 until arr.length()) {
                val o = arr.getJSONObject(k)
                if (o.getInt("ayah") == i) {
                    val t = o.getString("text").trim()
                    if (t.isNotEmpty()) return if (i == a) t else "(تفسير هذه الآية مذكور مع الآية $i)\n\n$t"
                }
            }
            i--
        }
        return null
    }
}
