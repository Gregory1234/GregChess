package gregc.gregchess

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

enum class GregLevel {
    LOW_DEBUG, MID_DEBUG, HIGH_DEBUG, IO_DEBUG, WARNING
}

class GregLogger(private val logger: Logger, private val logFile: File) {
    var level: GregLevel = GregLevel.WARNING
    private fun log(level: GregLevel, vararg vs: Any?) {
        val source = Thread.currentThread().stackTrace[3].className
        if (level >= GregLevel.WARNING) {
            logger.warning("[$source] " + vs.joinToString(" ") { it.toString() })
        } else if (this.level <= level) {
            logger.info("[$source] " + vs.joinToString(" ") { it.toString() })
        }

        logFile.appendText(
            "[${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)} $level][$source]: "
                    + vs.joinToString(" ") { it.toString() } + "\n")
    }

    fun low(vararg vs: Any?) = log(GregLevel.LOW_DEBUG, *vs)
    fun mid(vararg vs: Any?) = log(GregLevel.MID_DEBUG, *vs)
    fun high(vararg vs: Any?) = log(GregLevel.HIGH_DEBUG, *vs)
    fun io(vararg vs: Any?) = log(GregLevel.IO_DEBUG, *vs)
    fun warn(vararg vs: Any?) = log(GregLevel.WARNING, *vs)
}