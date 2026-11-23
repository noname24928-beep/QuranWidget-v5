package com.example.quranwidget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Qibla

class CompassView(c: Context) : View(c) {
    var heading = 0f
    var qibla = 0f
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.parseColor("#0B3D2E") }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 42f; textAlign = Paint.Align.CENTER; color = Color.parseColor("#0B3D2E") }
    private val needle = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 14f; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#C28A00") }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = Math.min(cx, cy) - 30f
        c.drawCircle(cx, cy, r, ring)
        // الشمال
        c.save(); c.rotate(-heading, cx, cy)
        c.drawText("N", cx, cy - r + 55f, txt)
        c.restore()
        // سهم القبلة
        c.save(); c.rotate(qibla - heading, cx, cy)
        c.drawLine(cx, cy, cx, cy - r + 20f, needle)
        c.drawText("🕋", cx, cy - r + 10f, txt)
        c.restore()
        c.drawCircle(cx, cy, 14f, needle)
    }
}

class QiblaTab(a: MainActivity) : Tab(a), SensorEventListener {
    private var sm: SensorManager? = null
    private var compass: CompassView? = null
    private var info: TextView? = null
    private var declination = 0f
    private var smooth = 0f
    private var bearing = 0f

    override fun build(): View {
        val box = act.vbox()
        val lat = P.lat(act)
        val lon = P.lon(act)
        bearing = Qibla(Coordinates(lat, lon)).direction.toFloat()
        declination = GeomagneticField(lat.toFloat(), lon.toFloat(), 0f, System.currentTimeMillis()).declination
        box.put(act.tv("اتجاه القبلة", 20f, "#0B3D2E", true))
        info = act.tv(String.format("القبلة: %.0f° من الشمال الحقيقي", bearing), 15f)
        box.put(info!!)
        compass = CompassView(act).apply { qibla = bearing }
        box.addView(compass, LinearLayout.LayoutParams(-1, act.dp(340)))
        box.put(act.tv("ثبّت الهاتف بشكل أفقي بعيداً عن المعادن والمغناطيس، وحرّكه على شكل رقم 8 لمعايرة البوصلة. السهم الذهبي يشير إلى الكعبة.", 13f, "#555555"))
        return box
    }

    override fun onShow() {
        sm = act.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val s = sm!!.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (s == null) { info?.text = "الهاتف لا يحتوي على حساس بوصلة"; return }
        sm!!.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onHide() { sm?.unregisterListener(this) }

    override fun onSensorChanged(e: SensorEvent) {
        val rm = FloatArray(9)
        val o = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rm, e.values)
        SensorManager.getOrientation(rm, o)
        var az = Math.toDegrees(o[0].toDouble()).toFloat() + declination
        az = (az + 360f) % 360f
        var diff = az - smooth
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        smooth = (smooth + diff * 0.15f + 360f) % 360f
        compass?.let { it.heading = smooth; it.invalidate() }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}
