package com.example.quranwidget

import android.app.WallpaperManager
import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.work.Worker
import androidx.work.WorkerParameters

object LockScreen {
    /** يرسم الآية على صورة ويضعها كخلفية لشاشة القفل فقط */
    fun apply(ctx: Context, force: Boolean) {
        val v = Quran.current(ctx, force)
        val dm = ctx.resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF0F5A42.toInt(), 0xFF05201A.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)

        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = ctx.resources.getFont(R.font.amiri_quran)
        }
        val width = (w * 0.84f).toInt()
        var size = w * 0.075f
        fun build(): StaticLayout {
            tp.textSize = size
            return StaticLayout.Builder.obtain(v.display, 0, v.display.length, tp, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(0f, 1.35f)
                .build()
        }
        var layout = build()
        while (layout.height > h * 0.40f && size > w * 0.03f) {
            size *= 0.92f
            layout = build()
        }
        val top = h * 0.52f
        c.save()
        c.translate((w - width) / 2f, top)
        layout.draw(c)
        c.restore()

        val refPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFD98E.toInt()
            textSize = w * 0.042f
            textAlign = Paint.Align.CENTER
            typeface = ctx.resources.getFont(R.font.amiri_quran)
        }
        c.drawText(v.ref, w / 2f, top + layout.height + w * 0.09f, refPaint)

        WallpaperManager.getInstance(ctx).setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK)
    }
}

class LockWorker(ctx: Context, p: WorkerParameters) : Worker(ctx, p) {
    override fun doWork(): Result = try {
        LockScreen.apply(applicationContext, true)
        VerseWidget.refreshAll(applicationContext, false)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
