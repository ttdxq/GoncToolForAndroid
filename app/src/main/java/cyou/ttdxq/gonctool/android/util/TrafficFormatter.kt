package cyou.ttdxq.gonctool.android.util

import java.util.Locale

private const val KIBIBYTE = 1024.0
private const val MEBIBYTE = KIBIBYTE * 1024.0
private const val GIBIBYTE = MEBIBYTE * 1024.0

fun formatTrafficRate(bytesPerSecond: Double, locale: Locale = Locale.getDefault()): String {
    val safeValue = bytesPerSecond.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    return when {
        safeValue >= GIBIBYTE -> String.format(locale, "%.1f GiB/s", safeValue / GIBIBYTE)
        safeValue >= MEBIBYTE -> String.format(locale, "%.1f MiB/s", safeValue / MEBIBYTE)
        safeValue >= KIBIBYTE -> String.format(locale, "%.1f KiB/s", safeValue / KIBIBYTE)
        else -> String.format(locale, "%.0f B/s", safeValue)
    }
}

fun formatTrafficBytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
    val safeValue = bytes.coerceAtLeast(0).toDouble()
    return when {
        safeValue >= GIBIBYTE -> String.format(locale, "%.1f GiB", safeValue / GIBIBYTE)
        safeValue >= MEBIBYTE -> String.format(locale, "%.1f MiB", safeValue / MEBIBYTE)
        safeValue >= KIBIBYTE -> String.format(locale, "%.1f KiB", safeValue / KIBIBYTE)
        else -> String.format(locale, "%.0f B", safeValue)
    }
}

fun formatElapsedDuration(elapsedSeconds: Long): String {
    val totalSeconds = elapsedSeconds.coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%02d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}
