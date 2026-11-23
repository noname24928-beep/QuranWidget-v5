package com.example.quranwidget

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.Outline
import android.view.ViewOutlineProvider
import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.URL

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.vbox(pad: Int = 16) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    layoutDirection = View.LAYOUT_DIRECTION_RTL
    setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
}

fun Context.hbox() = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    layoutDirection = View.LAYOUT_DIRECTION_RTL
}

fun Context.tv(text: String, size: Float = 15f, color: String = "#1E2B25", bold: Boolean = false) =
    TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.parseColor(color))
        if (bold) {
            typeface = resources.getFont(R.font.amiri_quran)
            if (size >= 16f) tag = if (size >= 20f) "title" else "h"
            if (size >= 16f && color == "#0B3D2E") setTextColor(T.EMERALD)
        }
    }

fun Context.btn(label: String, onClick: () -> Unit) = Button(this).apply {
    text = label
    isAllCaps = false
    textSize = 14f
    setTextColor(Color.WHITE)
    stateListAnimator = null
    minHeight = dp(44)
    setPadding(dp(14), dp(8), dp(14), dp(8))
    background = android.graphics.drawable.RippleDrawable(
        android.content.res.ColorStateList.valueOf(Color.argb(90, 241, 223, 166)),
        rounded(T.EMERALD, dpf(14f)), null
    )
    setOnClickListener { onClick() }
}

fun LinearLayout.put(v: View, top: Int = 8): View {
    addView(
        v,
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = context.dp(top) }
    )
    return v
}

fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

abstract class Tab(val act: MainActivity) {
    open val scrolls: Boolean get() = true
    open val cardWrap: Boolean get() = true
    open val cardify: Boolean get() = true
    abstract fun build(): View
    open fun onShow() {}
    open fun onHide() {}
}

object Pickers {
    fun audio(act: MainActivity, kind: String, onChosen: (File, String) -> Unit) {
        val items = Catalog.all(act, kind)
        val labels = items.map { (if (Catalog.file(act, it).exists()) "✓ " else "⬇ ") + it.name } +
            listOf("➕ إضافة برابط تحميل مباشر", "📁 اختيار ملف من الهاتف")
        AlertDialog.Builder(act).setTitle("اختر الصوت")
            .setItems(labels.toTypedArray()) { _, i ->
                when {
                    i < items.size -> fetch(act, items[i], onChosen)
                    i == items.size -> addUrl(act, kind, onChosen)
                    else -> act.pickAudioFile { uri -> importLocal(act, uri, kind, onChosen) }
                }
            }.show()
    }

    fun fetch(act: MainActivity, item: Item, onChosen: (File, String) -> Unit) {
        val f = Catalog.file(act, item)
        if (f.exists()) { onChosen(f, item.name); return }
        val pd = ProgressDialog(act)
        pd.setMessage("جاري تحميل ${item.name}")
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        pd.max = 100
        pd.setCancelable(false)
        pd.show()
        Downloader.download(act, item, { pd.progress = it }, { file ->
            pd.dismiss()
            if (file != null) onChosen(file, item.name) else act.toast("فشل التحميل، تأكد من الرابط والإنترنت")
        })
    }

    private fun addUrl(act: MainActivity, kind: String, onChosen: (File, String) -> Unit) {
        val box = act.vbox(16)
        val name = EditText(act).apply { hint = "الاسم (مثال: الشيخ محمد رفعت)" }
        val url = EditText(act).apply { hint = "رابط مباشر لملف mp3"; setSingleLine(true) }
        box.put(name); box.put(url)
        AlertDialog.Builder(act).setTitle("إضافة صوت جديد").setView(box)
            .setPositiveButton("تحميل") { _, _ ->
                val u = url.text.toString().trim()
                val n = name.text.toString().trim().ifEmpty { "صوت مضاف" }
                if (u.startsWith("http")) {
                    val item = Item(n, u, kind)
                    Catalog.addCustom(act, item)
                    fetch(act, item, onChosen)
                } else act.toast("الرابط غير صحيح")
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun importLocal(act: MainActivity, uri: Uri, kind: String, onChosen: (File, String) -> Unit) {
        Thread {
            try {
                var name = "ملف من الهاتف"
                act.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) name = c.getString(idx).substringBeforeLast(".")
                }
                val fileName = "local_${System.currentTimeMillis()}.mp3"
                val out = File(Catalog.dir(act), fileName)
                act.contentResolver.openInputStream(uri)!!.use { ins -> out.outputStream().use { ins.copyTo(it) } }
                Catalog.addCustom(act, Item(name, "local:$fileName", kind))
                act.runOnUiThread { onChosen(out, name) }
            } catch (e: Exception) {
                act.runOnUiThread { act.toast("تعذر قراءة الملف") }
            }
        }.start()
    }

    /** اختيار قارئ وسورة من mp3quran.net ثم تحميلها */
    fun quran(act: MainActivity, kind: String, onChosen: (File, String) -> Unit) {
        act.toast("جاري تحميل قائمة القراء…")
        Thread {
            try {
                val txt = URL("https://www.mp3quran.net/api/v3/reciters?language=ar").readText()
                val arr = JSONObject(txt).getJSONArray("reciters")
                act.runOnUiThread {
                    val names = Array(arr.length()) { arr.getJSONObject(it).getString("name") }
                    AlertDialog.Builder(act).setTitle("اختر القارئ").setItems(names) { _, i ->
                        val r = arr.getJSONObject(i)
                        pickMoshaf(act, r.getString("name"), r.getJSONArray("moshaf"), kind, onChosen)
                    }.show()
                }
            } catch (e: Exception) {
                act.runOnUiThread { act.toast("تعذر الاتصال بالإنترنت") }
            }
        }.start()
    }

    private fun pickMoshaf(act: MainActivity, rn: String, ms: org.json.JSONArray, kind: String, cb: (File, String) -> Unit) {
        if (ms.length() == 0) return
        if (ms.length() == 1) { pickSurah(act, rn, ms.getJSONObject(0), kind, cb); return }
        val names = Array(ms.length()) { ms.getJSONObject(it).getString("name") }
        AlertDialog.Builder(act).setTitle("الرواية").setItems(names) { _, i ->
            pickSurah(act, rn, ms.getJSONObject(i), kind, cb)
        }.show()
    }

    private fun pickSurah(act: MainActivity, rn: String, m: JSONObject, kind: String, cb: (File, String) -> Unit) {
        val nums = m.getString("surah_list").split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..114 }
        var server = m.getString("server")
        if (!server.endsWith("/")) server += "/"
        val labels = nums.map { "$it - ${Quran.names[it - 1]}" }.toTypedArray()
        AlertDialog.Builder(act).setTitle("اختر السورة").setItems(labels) { _, i ->
            val n = nums[i]
            val item = Item("$rn - ${Quran.names[n - 1]}", server + String.format("%03d.mp3", n), kind)
            Catalog.addCustom(act, item)
            fetch(act, item, cb)
        }.show()
    }
}


object Pal {
    val EMERALD = Color.parseColor("#0B3D2E")
    val EMERALD2 = Color.parseColor("#12795A")
    val DARK = Color.parseColor("#072A20")
    val GOLD = Color.parseColor("#C9A227")
    val GOLD_SOFT = Color.parseColor("#F1DE9A")
    val CREAM = Color.parseColor("#F6EFDC")
    val CARD = Color.parseColor("#FFFDF7")
    val MUTED = Color.parseColor("#6B7A70")
}

object Fonts {
    private var tf: Typeface? = null
    fun amiri(c: Context): Typeface = tf ?: c.resources.getFont(R.font.amiri_quran).also { tf = it }
}

object Art {
    /** بلاط زخرفة إسلامية (نجمة ثمانية) قابل للتكرار */
    fun tile(c: Context, bg: Int, line: Int, dp: Int = 72): BitmapDrawable {
        val px = c.dp(dp)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(bg)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = c.dp(1) * 0.9f
            color = line
        }
        fun star(cx: Float, cy: Float, r: Float) {
            val path = Path()
            val inner = r * 0.72f
            for (i in 0 until 16) {
                val a = Math.toRadians(i * 22.5 - 90.0)
                val rad = if (i % 2 == 0) r else inner
                val x = cx + (rad * Math.cos(a)).toFloat()
                val y = cy + (rad * Math.sin(a)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            cv.drawPath(path, p)
        }
        val f = px.toFloat()
        val r = f * 0.46f
        star(f / 2, f / 2, r)
        star(0f, 0f, r); star(f, 0f, r); star(0f, f, r); star(f, f, r)
        cv.drawCircle(f / 2, f / 2, r * 0.28f, p)
        return BitmapDrawable(c.resources, bmp).apply {
            setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    fun page(c: Context): Drawable = tile(c, Pal.CREAM, Color.argb(46, 201, 162, 39), 84)

    fun cardBg(c: Context, radius: Int = 20): Drawable = GradientDrawable().apply {
        setColor(Pal.CARD)
        cornerRadius = c.dp(radius).toFloat()
        setStroke(c.dp(1), Color.argb(130, 201, 162, 39))
    }

    fun hero(c: Context): Drawable {
        val shade = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(110, 3, 25, 18))
        )
        return LayerDrawable(arrayOf(tile(c, Pal.EMERALD2, Color.argb(70, 241, 222, 154), 64), shade))
    }

    fun chip(c: Context): Drawable = GradientDrawable().apply {
        setColor(Color.argb(60, 255, 255, 255))
        cornerRadius = c.dp(14).toFloat()
    }

    fun header(c: Context): Drawable = tile(c, Pal.EMERALD, Color.argb(60, 241, 222, 154), 48)
}

fun View.round(radiusDp: Int) {
    val r = context.dp(radiusDp).toFloat()
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, r) }
    }
    clipToOutline = true
}

/** بطاقة بخلفية مزخرفة وحدود ذهبية */
fun Context.card(pad: Int = 16): LinearLayout = vbox(pad).apply {
    background = Art.cardBg(this@card)
    elevation = dp(2).toFloat()
}

fun Context.sectionTitle(text: String) = tv(text, 21f, "#0B3D2E", true)

fun Context.gap(h: Int = 12) = View(this).apply { minimumHeight = dp(h) }
