package com.example.quranwidget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Switch
import android.widget.TextView

/** ألوان وتصميم التطبيق: زمرد عميق + ذهب + ورق كريمي */
object T {
    val EMERALD = Color.parseColor("#0F5A42")
    val DEEP = Color.parseColor("#0A3D2E")
    val NIGHT = Color.parseColor("#062219")
    val GOLD = Color.parseColor("#C9A24A")
    val GOLD_LIGHT = Color.parseColor("#F1DFA6")
    val CREAM = Color.parseColor("#F6F0E1")
    val CARD = Color.parseColor("#FFFDF8")
    val STROKE = Color.parseColor("#E6D9B8")
    val INK = Color.parseColor("#1E2B25")
    val MUTED = Color.parseColor("#6F7D75")
}

fun Context.dpf(v: Float) = v * resources.displayMetrics.density

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeW: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (stroke != 0) setStroke(strokeW, stroke)
    }

/** بطاقة بيضاء مزخرفة بحد ذهبي خفيف وظل */
fun Context.card(pad: Int = 14): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    layoutDirection = View.LAYOUT_DIRECTION_RTL
    background = rounded(T.CARD, dpf(18f), T.STROKE, dp(1))
    elevation = dpf(3f)
    setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
}

/** زخرفة نجمة ثمانية على خلفية متدرجة زمردية */
class PatternBg(c: Context, private val round: Float = 0f) : View(c) {
    private val star = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = c.dpf(1.1f)
        color = Color.argb(46, 241, 223, 166)
    }
    private val fill = Paint()
    private val cell = c.dpf(46f)
    private val rad = c.dpf(14f)

    init {
        if (round > 0f) {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, round)
                }
            }
            clipToOutline = true
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        fill.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), T.EMERALD, T.NIGHT, Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        val rows = (height / cell).toInt() + 2
        val cols = (width / cell).toInt() + 2
        for (y in 0..rows) for (x in 0..cols) {
            val cx = x * cell + if (y % 2 == 1) cell / 2 else 0f
            val cy = y * cell
            c.save()
            c.translate(cx, cy)
            c.drawRect(-rad, -rad, rad, rad, star)
            c.rotate(45f)
            c.drawRect(-rad, -rad, rad, rad, star)
            c.restore()
        }
    }
}

object Styler {
    private fun states(on: Int, off: Int) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(on, off)
    )

    /** تنسيق عناصر النظام (مفاتيح، خانات اختيار، حقول) بألوان التطبيق */
    fun style(v: View) {
        when (v) {
            is Switch -> {
                v.thumbTintList = states(T.GOLD, Color.parseColor("#BDB7A8"))
                v.trackTintList = states(Color.parseColor("#7FA08F"), Color.parseColor("#DDD6C4"))
                v.setTextColor(T.INK)
            }
            is CheckBox -> { v.buttonTintList = states(T.EMERALD, T.MUTED); v.setTextColor(T.INK) }
            is RadioButton -> { v.buttonTintList = states(T.EMERALD, T.MUTED); v.setTextColor(T.INK) }
            is EditText -> v.setTextColor(T.INK)
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) style(v.getChildAt(i))
    }

    /** يحوّل الشاشة المسطحة إلى بطاقات: كل عنوان فرعي يبدأ بطاقة جديدة */
    fun cardify(ctx: Context, root: LinearLayout) {
        val kids = ArrayList<View>()
        for (i in 0 until root.childCount) kids.add(root.getChildAt(i))
        root.removeAllViews()
        var card: LinearLayout? = null
        fun newCard(): LinearLayout {
            val c = ctx.card()
            root.addView(c, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(12) })
            return c
        }
        for (v in kids) {
            when (v.tag) {
                "title" -> { root.addView(v); card = null }
                "h" -> {
                    if (card == null || card.childCount > 0) card = newCard()
                    (v.layoutParams as? LinearLayout.LayoutParams)?.topMargin = 0
                    card.addView(v)
                    // خط ذهبي تحت العنوان
                    card.addView(View(ctx).apply { setBackgroundColor(T.GOLD) },
                        LinearLayout.LayoutParams(ctx.dp(40), ctx.dp(2)).apply { topMargin = ctx.dp(6); bottomMargin = ctx.dp(4) })
                }
                else -> {
                    if (card == null) card = newCard()
                    card.addView(v)
                }
            }
        }
    }
}
