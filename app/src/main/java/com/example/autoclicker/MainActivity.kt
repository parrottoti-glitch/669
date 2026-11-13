package com.example.autoclicker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("cfg", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private lateinit var fInterval: EditText
    private lateinit var fCount: EditText
    private lateinit var fH: EditText
    private lateinit var fM: EditText
    private lateinit var fS: EditText
    private lateinit var fMs: EditText
    private lateinit var fPre: EditText
    private lateinit var fAddr: EditText
    private lateinit var swSched: Switch
    private lateinit var swNtp: Switch
    private lateinit var tvSvc: TextView
    private lateinit var tvClock: TextView
    private lateinit var tvNtp: TextView

    private var curSrc = 1 // 0 = NTP ، 1 = سایت (tsetmc)

    private fun addrKey(s: Int) = if (s == 1) "addr1" else "addr0"
    private fun defAddr(s: Int) = if (s == 1) TimeSync.DEFAULT_HTTP else TimeSync.DEFAULT_NTP

    private val tick = object : Runnable {
        override fun run() {
            val off = if (swNtp.isChecked) prefs.getLong("ntp_off", 0L) else 0L
            tvClock.text = fmt.format(Date(System.currentTimeMillis() + off))
            handler.postDelayed(this, 40)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun EditText.num(def: Long): Long = text.toString().trim().toLongOrNull() ?: def

    private fun label(t: String) = TextView(this).apply {
        text = t
        setTextColor(0xFFB0B0C0.toInt())
        textSize = 14f
        setPadding(0, dp(16), 0, dp(4))
    }

    private fun field(hint: String, v: Long) = EditText(this).apply {
        setText(v.toString())
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF777788.toInt())
        gravity = Gravity.CENTER
        textSize = 18f
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        setOnClickListener { onClick() }
    }

    private fun col(title: String, f: EditText) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@MainActivity).apply {
            text = title
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFF8888A0.toInt())
        })
        addView(f)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        root.addView(TextView(this).apply {
            text = "اتوکلیکر"
            textSize = 28f
            setTextColor(Color.WHITE)
        })

        tvSvc = TextView(this).apply { textSize = 15f; setPadding(0, dp(12), 0, dp(4)) }
        root.addView(tvSvc)
        root.addView(button("فعال‌سازی سرویس دسترسی") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        root.addView(label("فاصله‌ی بین کلیک‌ها (میلی‌ثانیه)"))
        fInterval = field("100", prefs.getLong("interval", 100L))
        root.addView(fInterval)

        root.addView(label("تعداد ضربه‌ها (۰ = بی‌نهایت)"))
        fCount = field("0", prefs.getInt("count", 0).toLong())
        val countRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        countRow.addView(button("−") {
            fCount.setText(maxOf(0L, fCount.num(0) - 1).toString())
        })
        countRow.addView(fCount, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        countRow.addView(button("+") {
            fCount.setText((fCount.num(0) + 1).toString())
        })
        root.addView(countRow)

        root.addView(label("زمان‌بندی شروع"))
        swSched = Switch(this).apply {
            text = "شروع خودکار در ساعت مشخص"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("sched", false)
        }
        root.addView(swSched)

        fH = field("ساعت", prefs.getInt("h", 8).toLong())
        fM = field("دقیقه", prefs.getInt("m", 30).toLong())
        fS = field("ثانیه", prefs.getInt("s", 0).toLong())
        fMs = field("ms", prefs.getInt("ms", 50).toLong())
        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val lp = { LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        timeRow.addView(col("ساعت", fH), lp())
        timeRow.addView(col("دقیقه", fM), lp())
        timeRow.addView(col("ثانیه", fS), lp())
        timeRow.addView(col("میلی‌ثانیه", fMs), lp())
        root.addView(timeRow)

        root.addView(label("پیش‌دستی / جبران تأخیر (میلی‌ثانیه، کلیک زودتر شروع می‌شود)"))
        fPre = field("0", prefs.getLong("pre", 0L))
        root.addView(fPre)

        // ---- ساعت مرجع ----
        root.addView(label("ساعت مرجع"))
        swNtp = Switch(this).apply {
            text = "استفاده از ساعت مرجع برای زمان‌بندی"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("ntp_use", false)
        }
        root.addView(swNtp)

        curSrc = prefs.getInt("tsrc", 1)
        val rg = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val rbHttp = RadioButton(this).apply {
            id = View.generateViewId()
            text = "ساعت سایت (پیش‌فرض: بورس ایران tsetmc.com)"
            setTextColor(Color.WHITE)
        }
        val rbNtp = RadioButton(this).apply {
            id = View.generateViewId()
            text = "ساعت NTP (دقیق‌ترین، مثل قبل)"
            setTextColor(Color.WHITE)
        }
        rg.addView(rbHttp)
        rg.addView(rbNtp)
        rg.check(if (curSrc == 1) rbHttp.id else rbNtp.id)
        root.addView(rg)

        fAddr = EditText(this).apply {
            setText(prefs.getString(addrKey(curSrc), defAddr(curSrc)))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF777788.toInt())
            hint = "آدرس سایت یا سرور NTP"
            textSize = 15f
            textDirection = View.TEXT_DIRECTION_LTR
            gravity = Gravity.START
        }
        root.addView(fAddr)

        rg.setOnCheckedChangeListener { _, checked ->
            val n = if (checked == rbHttp.id) 1 else 0
            if (n != curSrc) {
                prefs.edit().putString(addrKey(curSrc), fAddr.text.toString().trim()).apply()
                curSrc = n
                fAddr.setText(prefs.getString(addrKey(n), defAddr(n)))
            }
        }

        tvClock = TextView(this).apply {
            textSize = 30f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_LTR
            setTextColor(0xFF4CAF50.toInt())
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(tvClock)
        tvNtp = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF8888A0.toInt())
        }
        root.addView(tvNtp)
        root.addView(button("همگام‌سازی ساعت") { doSync() })
        showNtpInfo()

        root.addView(View(this), LinearLayout.LayoutParams(1, dp(20)))
        root.addView(button("نمایش نوار شناور و هدف") {
            val s = ClickService.instance
            if (s == null) {
                Toast.makeText(this, "اول سرویس دسترسی را فعال کن", Toast.LENGTH_LONG).show()
            } else {
                save()
                s.showOverlay()
                moveTaskToBack(true)
            }
        })

        root.addView(TextView(this).apply {
            text = "دایره‌ی قرمز را روی نقطه‌ی دلخواه ببر و با دکمه‌ی ▶ شروع کن."
            textSize = 13f
            setTextColor(0xFF8888A0.toInt())
            setPadding(0, dp(12), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF0B0B0F.toInt())
            isFillViewport = true
            addView(root)
        })
    }

    private fun doSync() {
        save()
        tvNtp.text = if (curSrc == 1) "در حال همگام‌سازی با سایت… (حدود ۵ ثانیه)" else "در حال همگام‌سازی…"
        val self = this
        Thread {
            val r = TimeSync.run(self)
            runOnUiThread {
                if (r == null) {
                    tvNtp.text = if (curSrc == 1)
                        "ناموفق بود. آدرس سایت یا اینترنت را چک کن، یا NTP را امتحان کن."
                    else
                        "ناموفق بود. شاید شبکه NTP را می‌بندد؛ ساعت سایت را امتحان کن."
                } else {
                    prefs.edit().putBoolean("ntp_use", true).apply()
                    swNtp.isChecked = true
                    showNtpInfo()
                }
            }
        }.start()
    }

    private fun showNtpInfo() {
        if (prefs.getLong("ntp_time", 0L) == 0L) {
            tvNtp.text = "هنوز همگام‌سازی نشده"
            return
        }
        val off = prefs.getLong("ntp_off", 0L)
        val err = prefs.getLong("ntp_err", 0L)
        val d = when {
            off > 0 -> "ساعت گوشی $off میلی‌ثانیه عقب است"
            off < 0 -> "ساعت گوشی ${-off} میلی‌ثانیه جلو است"
            else -> "ساعت گوشی دقیق است"
        }
        tvNtp.text = "$d (دقت تقریبی ±$err میلی‌ثانیه)"
    }

    private fun save() {
        prefs.edit()
            .putLong("interval", fInterval.num(100).coerceAtLeast(1))
            .putInt("count", fCount.num(0).toInt().coerceAtLeast(0))
            .putBoolean("sched", swSched.isChecked)
            .putInt("h", fH.num(0).toInt().coerceIn(0, 23))
            .putInt("m", fM.num(0).toInt().coerceIn(0, 59))
            .putInt("s", fS.num(0).toInt().coerceIn(0, 59))
            .putInt("ms", fMs.num(0).toInt().coerceIn(0, 999))
            .putLong("pre", fPre.num(0).coerceAtLeast(0))
            .putBoolean("ntp_use", swNtp.isChecked)
            .putInt("tsrc", curSrc)
            .putString(addrKey(curSrc), fAddr.text.toString().trim())
            .apply()
    }

    override fun onResume() {
        super.onResume()
        val on = ClickService.instance != null
        tvSvc.text = if (on) "سرویس دسترسی فعال است ✓" else "سرویس دسترسی غیرفعال است ✗"
        tvSvc.setTextColor(if (on) 0xFF4CAF50.toInt() else 0xFFFF5252.toInt())
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        save()
    }
}
