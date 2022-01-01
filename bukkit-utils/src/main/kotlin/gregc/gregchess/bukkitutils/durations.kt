package gregc.gregchess.bukkitutils

import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val Int.ticks: Duration get() = (this * 50).milliseconds
val Long.ticks: Duration get() = (this * 50).milliseconds

class DurationFormatException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

fun String.toDuration(): Duration = toDurationOrNull() ?: throw DurationFormatException(this)

fun String.toDurationOrNull(): Duration? {
    val match1 = Regex("""^(-|\+|)(\d+(?:\.\d+)?)(s|ms|t|m)$""").find(this)
    if (match1 != null) {
        val amount =
            (match1.groupValues[2].toDoubleOrNull()
                ?: return null) * (if (match1.groupValues[1] == "-") -1 else 1)
        return when (match1.groupValues[3]) {
            "s" -> amount.seconds
            "ms" -> amount.milliseconds
            "t" -> amount.roundToLong().ticks
            "m" -> amount.minutes
            else -> null
        }
    }
    val match2 = Regex("""^(-)?(\d+):(\d{2,}(?:\.\d)?)$""").find(this)
    if (match2 != null) {
        val sign = (if (match2.groupValues[1] == "-") -1 else 1)
        val minutes = (match2.groupValues[2].toLongOrNull() ?: return null) * sign
        val seconds = (match2.groupValues[3].toDoubleOrNull() ?: return null) * sign
        return minutes.minutes + seconds.seconds
    }
    return null
}

fun Duration.format(formatString: String): String = (if (isNegative()) Duration.ZERO else this).toComponents { hours, minutes, seconds, nanoseconds ->
    formatString.replace(Regex("""HH|mm|ss|S|SS|SSS|\w|'(\w*)'|"""")) {
        when(it.value) {
            "HH" -> hours.toString()
            "mm" -> minutes.toString().padStart(2,'0')
            "ss" -> seconds.toString().padStart(2,'0')
            "SSS" -> nanoseconds.toString().padStart(9, '0').take(3)
            "SS" -> nanoseconds.toString().padStart(9, '0').take(2)
            "S" -> nanoseconds.toString().padStart(9, '0').take(1)
            "n" -> nanoseconds.toString().padStart(9, '0')
            "t" -> (nanoseconds / 50000000).toString()
            "T" -> (inWholeNanoseconds / 50000000).toString()
            "\"" -> "'"
            else -> if (it.value.startsWith("'"))
                it.groupValues[0]
            else
                throw DurationFormatException(formatString)
        }
    }
}