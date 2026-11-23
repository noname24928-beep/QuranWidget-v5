package com.example.quranwidget

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    companion object {
        const val SCREEN_ADHKAR = 2
        const val SCREEN_MORE = 4
    }

    private var current: Tab? = null
    private var index = 0
    private lateinit var holder: FrameLayout
    private lateinit var titleTv: TextView
    private lateinit var backBtn: TextView
    private val navItems = ArrayList<Pair<TextView, TextView>>()
    private var pendingPick: ((Uri) -> Unit)? = null
    private var pendingLocate: ((Boolean) -> Unit)? = null

    /** كل الشاشات؛ أول 5 في الشريط السفلي والباقي من «المزيد» */
    private val screens: List<Pair<String, () -> Tab>> by lazy {
        listOf(
            "مواقيت الصلاة" to { PrayerTab(this) },
            "المصحف الشريف" to { MushafTab(this) },
            "الأذكار" to { AdhkarTab(this) },
            "المكتبة الصوتية" to { LibraryTab(this) },
            "المزيد" to { MoreTab(this) },
            "الأذان والمؤذنون" to { AdhanTab(this) },
            "اتجاه القبلة" to { QiblaTab(this) },
            "الإمساكية والتقويم" to { CalendarTab(this) },
            "رمضان" to { RamadanTab(this) },
            "الصلاة على النبي ﷺ" to { SalawatTab(this) },
            "آيات الودجت" to { VersesTab(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = Art.page(this@MainActivity)
        }

        // ---- الترويسة ----
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            background = Art.header(this@MainActivity)
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        backBtn = TextView(this).apply {
            text = "›"; textSize = 32f; setTextColor(Pal.GOLD_SOFT); visibility = View.GONE
            setPadding(dp(6), 0, dp(14), dp(4))
            setOnClickListener { show(SCREEN_MORE) }
        }
        titleTv = TextView(this).apply {
            textSize = 25f; setTextColor(Color.WHITE); typeface = Fonts.amiri(this@MainActivity)
        }
        val logo = TextView(this).apply { text = "☪"; textSize = 26f; setTextColor(Pal.GOLD) }
        header.addView(backBtn)
        header.addView(titleTv, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(logo)
        root.addView(header)
        root.addView(View(this).apply { setBackgroundColor(Pal.GOLD) }, LinearLayout.LayoutParams(-1, dp(2)))

        holder = FrameLayout(this)
        root.addView(holder, LinearLayout.LayoutParams(-1, 0, 1f))

        // ---- الشريط السفلي ----
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Pal.DARK)
            setPadding(0, dp(6), 0, dp(6))
        }
        val items = listOf("🕌" to "المواقيت", "📖" to "المصحف", "📿" to "الأذكار", "🎧" to "المكتبة", "☰" to "المزيد")
        items.forEachIndexed { i, (icon, label) ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setOnClickListener { show(i) }
            }
            val ic = TextView(this).apply { text = icon; textSize = 22f; gravity = Gravity.CENTER }
            val lb = TextView(this).apply { text = label; textSize = 11f; gravity = Gravity.CENTER }
            col.addView(ic); col.addView(lb)
            bar.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            navItems.add(ic to lb)
        }
        root.addView(bar)
        setContentView(root)

        askNotifications(false)
        try { Scheduler.scheduleAll(this) } catch (e: Exception) { }
        show((intent?.getIntExtra("tab", 0) ?: 0).coerceIn(0, screens.size - 1))
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        show((intent?.getIntExtra("tab", 0) ?: 0).coerceIn(0, screens.size - 1))
    }

    fun show(i: Int) {
        index = i
        current?.onHide()
        val s = screens[i]
        val t = s.second()
        current = t
        titleTv.text = s.first
        backBtn.visibility = if (i > 4) View.VISIBLE else View.GONE
        val sel = if (i > 4) 4 else i
        navItems.forEachIndexed { k, (ic, lb) ->
            val on = k == sel
            ic.alpha = if (on) 1f else 0.55f
            lb.setTextColor(if (on) Pal.GOLD else Color.parseColor("#9FB8AD"))
        }

        holder.removeAllViews()
        val v = t.build()
        if (!t.scrolls) {
            holder.addView(v)
        } else {
            val wrap = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(16))
            }
            if (t.cardWrap) {
                v.background = Art.cardBg(this)
                v.elevation = dp(2).toFloat()
            }
            wrap.addView(v, LinearLayout.LayoutParams(-1, -2))
            holder.addView(ScrollView(this).apply { addView(wrap) })
        }
        t.onShow()
    }

    override fun onBackPressed() {
        if (index > 4) show(SCREEN_MORE) else super.onBackPressed()
    }

    override fun onResume() { super.onResume(); current?.onShow() }
    override fun onPause() { current?.onHide(); super.onPause() }

    fun askNotifications(announce: Boolean = true) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        else if (announce) toast("الإشعارات مفعّلة")
    }

    fun pickAudioFile(cb: (Uri) -> Unit) {
        pendingPick = cb
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "audio/*" }, 77
        )
    }

    @Deprecated("deprecated")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 77 && resultCode == RESULT_OK) data?.data?.let { pendingPick?.invoke(it) }
    }

    fun locate(done: (Boolean) -> Unit) {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingLocate = done
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), 2)
            return
        }
        doLocate(done)
    }

    @Deprecated("deprecated")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2) {
            val cb = pendingLocate
            pendingLocate = null
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (cb != null) doLocate(cb)
            } else cb?.invoke(false)
        }
    }

    private fun applyLoc(l: Location) {
        P.setPlace(this, "موقعك الحالي (%.2f, %.2f)".format(l.latitude, l.longitude), l.latitude, l.longitude)
        Scheduler.scheduleAll(this)
    }

    private fun doLocate(done: (Boolean) -> Unit) {
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var best: Location? = null
            for (p in lm.getProviders(true)) {
                val l = lm.getLastKnownLocation(p) ?: continue
                val b = best
                if (b == null || l.time > b.time) best = l
            }
            val found = best
            if (found != null) { applyLoc(found); done(true); return }
            val provider = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER
            else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else null
            if (provider == null) { done(false); return }
            toast("جاري تحديد الموقع…")
            lm.requestLocationUpdates(provider, 0L, 0f, object : LocationListener {
                override fun onLocationChanged(l: Location) {
                    lm.removeUpdates(this)
                    applyLoc(l); done(true)
                }
                @Deprecated("deprecated")
                override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {}
            })
        } catch (e: SecurityException) {
            done(false)
        }
    }
}
