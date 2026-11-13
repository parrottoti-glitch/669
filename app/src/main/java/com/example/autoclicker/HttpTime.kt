package com.example.autoclicker

import java.net.HttpURLConnection
import java.net.URL

/**
 * گرفتن ساعت از هدر Date یک سایت.
 * هدر Date فقط ثانیه دارد؛ برای رسیدن به میلی‌ثانیه چندین درخواست پشت‌سرهم می‌فرستیم
 * و بازه‌ی ممکن برای اختلاف ساعت را کوچک‌تر و کوچک‌تر می‌کنیم.
 */
object HttpTime {
    private class S(val w0: Double, val rtt: Double, val date: Long)

    fun sync(rawUrl: String, samples: Int = 40, gapMs: Long = 35, maxTotalMs: Long = 7000): TimeSync.Result? {
        var url = rawUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        val list = ArrayList<S>()
        val tStart = System.nanoTime()
        try {
            for (i in 0..samples) {
                if ((System.nanoTime() - tStart) / 1_000_000L > maxTotalMs) break
                val w0 = System.currentTimeMillis().toDouble()
                val n0 = System.nanoTime()
                val c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = "HEAD"
                c.connectTimeout = 3000
                c.readTimeout = 3000
                c.instanceFollowRedirects = false
                c.useCaches = false
                val code = c.responseCode
                val rtt = (System.nanoTime() - n0) / 1e6
                val date = c.date
                // نمونه‌ی اول شامل DNS و handshake است؛ کنار گذاشته می‌شود
                if (code > 0 && i > 0 && date > 0L) list.add(S(w0, rtt, date))
                Thread.sleep(gapMs)
            }
        } catch (e: Exception) {
            if (list.size < 8) return null
        }
        if (list.size < 8) return null

        val minRtt = list.minOf { it.rtt }
        val use = list.filter { it.rtt <= minRtt * 2 + 10 }
        // ساعت واقعی سرور در لحظه‌ی ساخت هدر بین w0 و w0+rtt (ساعت گوشی) بوده
        // و Date <= واقعی < Date+1000
        val lo = use.maxOf { it.date - (it.w0 + it.rtt) }
        val hi = use.minOf { it.date + 1000.0 - it.w0 }
        val off = Math.round((lo + hi) / 2.0)
        val err = Math.round(Math.abs(hi - lo) / 2.0)
        return TimeSync.Result(off, err)
    }
}
