package com.example.autoclicker

import android.content.Context

object TimeSync {
    /** offset: ساعت مرجع منهای ساعت گوشی (ms) ، err: دقت تقریبی ± (ms) */
    data class Result(val offset: Long, val err: Long)

    const val DEFAULT_NTP = "time.google.com"
    const val DEFAULT_HTTP = "https://tsetmc.com"

    fun run(ctx: Context): Result? {
        val p = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
        val src = p.getInt("tsrc", 1)
        val addr = (p.getString(if (src == 1) "addr1" else "addr0", "") ?: "").trim()
        val r = if (src == 1) {
            HttpTime.sync(if (addr.isEmpty()) DEFAULT_HTTP else addr)
        } else {
            Ntp.sync(5, if (addr.isEmpty()) DEFAULT_NTP else addr)
        }
        if (r != null) {
            p.edit()
                .putLong("ntp_off", r.offset)
                .putLong("ntp_err", r.err)
                .putLong("ntp_time", System.currentTimeMillis())
                .apply()
        }
        return r
    }
}
