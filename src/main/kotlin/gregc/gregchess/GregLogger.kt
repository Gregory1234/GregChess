package gregc.gregchess

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

@Suppress("unused")
class GregLogger(private val logFile: File, private val logger: Logger) {
    enum class Level {
        LOW_DEBUG, MID_DEBUG, HIGH_DEBUG, IO_DEBUG, WARNING, DEBUG
    }

    var level: Level = Level.WARNING
    private fun log(level: Level, vararg vs: Any?) {
        val source = Thread.currentThread().stackTrace[3].className
        if (level == Level.WARNING) {
            logger.warning("[$source] " + vs.joinToString(" ") { it.toString() })
        } else if (this.level <= level) {
            logger.info("[$source] " + vs.joinToString(" ") { it.toString() })
        }

        logFile.appendText(
            "[${
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } $level][$source]: "
                    + vs.joinToString(" ") { it.toString() } + "\n")
    }
    @Deprecated("Don't commit it!")
    fun debug(vararg vs: Any?) = log(Level.DEBUG, *vs)
    fun low(vararg vs: Any?) = log(Level.LOW_DEBUG, *vs)
    fun mid(vararg vs: Any?) = log(Level.MID_DEBUG, *vs)
    fun high(vararg vs: Any?) = log(Level.HIGH_DEBUG, *vs)
    fun io(vararg vs: Any?) = log(Level.IO_DEBUG, *vs)
    fun warn(vararg vs: Any?) = log(Level.WARNING, *vs)
}