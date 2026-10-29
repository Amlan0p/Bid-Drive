package app.biddrive.android.util

import java.time.Instant
import java.util.Locale

/** 4875000 -> "₹48,75,000" (Indian digit grouping). */
fun formatInr(amount: Double): String = formatInr(amount.toLong())

fun formatInr(amount: Long): String {
    val negative = amount < 0
    val digits = kotlin.math.abs(amount).toString()
    val grouped = if (digits.length <= 3) digits else {
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        "$rest,$last3"
    }
    return (if (negative) "-₹" else "₹") + grouped
}

/** 4875000 -> "₹48.75 L", 16400000 -> "₹1.64 Cr". Used for compact labels. */
fun formatInrShort(amount: Double): String = when {
    amount >= 1_00_00_000 -> String.format(Locale.US, "₹%.2f Cr", amount / 1_00_00_000)
    amount >= 1_00_000 -> String.format(Locale.US, "₹%.2f L", amount / 1_00_000)
    else -> formatInr(amount)
}

fun formatKm(km: Int): String = String.format(Locale.US, "%,d km", km)

fun parseInstantMillis(iso: String): Long = runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)

/** "02h 14m 36s", "14m 36s", "36s". */
fun formatCountdown(ms: Long): String {
    val total = (ms.coerceAtLeast(0)) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h >= 24 -> "${h / 24}d ${h % 24}h ${m}m"
        h > 0 -> String.format(Locale.US, "%02dh %02dm %02ds", h, m, s)
        m > 0 -> String.format(Locale.US, "%02dm %02ds", m, s)
        else -> String.format(Locale.US, "%02ds", s)
    }
}

fun formatRelativePast(iso: String, nowMs: Long = System.currentTimeMillis()): String {
    val diff = (nowMs - parseInstantMillis(iso)) / 1000
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60} min ago"
        diff < 86400 -> "${diff / 3600} h ago"
        else -> "${diff / 86400} d ago"
    }
}

fun titleCase(s: String): String = s.replaceFirstChar { it.uppercase() }

fun fuelLabel(f: String) = when (f) { "cng" -> "CNG"; "" -> "—"; else -> titleCase(f) }
fun transmissionLabel(t: String) = when (t) { "cvt" -> "CVT"; "" -> "—"; else -> titleCase(t) }

/** Minimal client-side validation shared by the auth screens. The server validates again. */
fun isValidEmail(email: String) = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$").matches(email.trim())
fun passwordProblem(pw: String): String? = when {
    pw.length < 8 -> "At least 8 characters"
    !pw.any { it.isLetter() } || !pw.any { it.isDigit() } -> "Must contain a letter and a number"
    else -> null
}
