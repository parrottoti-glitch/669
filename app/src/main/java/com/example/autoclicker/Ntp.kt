package com.example.autoclicker

import android.content.Context
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object Ntp {
    private val servers = listOf(
        "time.google.com",
        "time.cloudflare.com",
        "pool.ntp.org",
        "time.windows.com"
    )
    private const val EPOCH_DIFF = 2208988800L

    /** اختلاف ساعت (ms): ساعت مرجع منهای ساعت گوشی. مثبت یعنی گوشی عقب است. */
    fun offset(ctx: Context): Long {
        val p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        return if (p.getBoolean("ntp_use", false)) p.getLong("ntp_off", 0L) else 0L
    }

    private fun readTs(b: ByteArray, off: Int): Double {
        var sec = 0L
        var frac = 0L
        for (i in 0 until 4) sec = (sec shl 8) or (b[off + i].toLong() and 0xFF)
        for (i in 4 until 8) frac = (frac shl 8) or (b[off + i].toLong() and 0xFF)
        if (sec == 0L && frac == 0L) return 0.0
        return (sec - EPOCH_DIFF) * 1000.0 + frac * 1000.0 / 4294967296.0
    }

    /** برمی‌گرداند (offset, delay) به میلی‌ثانیه یا null */
    private fun query(host: String, timeoutMs: Int = 1500): Pair<Double, Double>? {
        var s: DatagramSocket? = null
        return try {
            s = DatagramSocket()
            s.soTimeout = timeoutMs
            val addr = InetAddress.getByName(host)
            val buf = ByteArray(48)
            buf[0] = 0x1B
            val t0 = System.currentTimeMillis().toDouble()
            val n0 = System.nanoTime()
            s.send(DatagramPacket(buf, buf.size, addr, 123))
            val resp = DatagramPacket(ByteArray(48), 48)
            s.receive(resp)
            val t3 = t0 + (System.nanoTime() - n0) / 1e6
            val d = resp.data
            if (d[1].toInt() == 0) return null
            val t1 = readTs(d, 32)
            val t2 = readTs(d, 40)
            if (t1 == 0.0 || t2 == 0.0) return null
            val offset = ((t1 - t0) + (t2 - t3)) / 2.0
            val delay = (t3 - t0) - (t2 - t1)
            Pair(offset, delay)
        } catch (e: Exception) {
            null
        } finally {
            s?.close()
        }
    }

    /** اگر host داده شود فقط همان سرور استفاده می‌شود. */
    fun sync(samples: Int = 5, host: String? = null): TimeSync.Result? {
        val list = if (host.isNullOrBlank()) servers else listOf(host.trim())
        var best: Pair<Double, Double>? = null
        for (h in list) {
            for (i in 0 until samples) {
                val r = query(h) ?: break
                val b = best
                if (b == null || r.second < b.second) best = r
            }
            if (best != null) break
        }
        return best?.let { TimeSync.Result(Math.round(it.first), Math.round(Math.abs(it.second) / 2.0)) }
    }
}
