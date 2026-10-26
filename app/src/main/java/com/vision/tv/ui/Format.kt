package com.vision.tv.ui

import java.util.Locale

/** 3725000 ms -> "1:02:05"; 125000 ms -> "2:05". */
fun clock(ms: Long): String {
    val t = ms.coerceAtLeast(0L) / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

/** 6480 s -> "1 h 48 min"; 2400 s -> "40 min"; 0 -> "". */
fun minutesText(sec: Int): String {
    if (sec <= 0) return ""
    val m = sec / 60
    return if (m >= 60) String.format(Locale.US, "%d h %02d min", m / 60, m % 60) else "$m min"
}

/** "Quedan 23 min" / "Quedan 1 h 05 min". */
fun remainingText(posMs: Long, durMs: Long): String {
    if (durMs <= 0) return ""
    val left = ((durMs - posMs) / 60_000).toInt()
    return when {
        left <= 0 -> "Casi terminada"
        left < 60 -> "Quedan $left min"
        else -> String.format(Locale.US, "Quedan %d h %02d min", left / 60, left % 60)
    }
}
