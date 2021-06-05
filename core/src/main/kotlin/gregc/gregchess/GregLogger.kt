package gregc.gregchess

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

@Suppress("unused")
interface GregLogger {
    enum class Level {
        LOW_DEBUG, MID_DEBUG, HIGH_DEBUG, IO_DEBUG, WARNING, DEBUG
    }

    var level: Level

    fun sendLog(level: Level, source: String, vararg vs: Any?)

    fun log(level: Level, vararg vs: Any?) {
        val source = Thread.currentThread().stackTrace[5].className
        sendLog(level, source, *vs)
    }
    @Deprecated("Don't commit it!")
    fun debug(vararg vs: Any?) = log(Level.DEBUG, *vs)
    fun low(vararg vs: Any?) = log(Level.LOW_DEBUG, *vs)
    fun mid(vararg vs: Any?) = log(Level.MID_DEBUG, *vs)
    fun high(vararg vs: Any?) = log(Level.HIGH_DEBUG, *vs)
    fun io(vararg vs: Any?) = log(Level.IO_DEBUG, *vs)
    fun warn(vararg vs: Any?) = log(Level.WARNING, *vs)
}

class JavaGregLogger(private val logger: Logger): GregLogger {
    override var level = GregLogger.Level.WARNING

    override fun sendLog(level: GregLogger.Level, source: String, vararg vs: Any?) {
        if (level == GregLogger.Level.WARNING) {
            logger.warning("[$source] " + vs.joinToString(" ") { it.toString() })
        } else if (this.level <= level) {
            logger.info("[$source] " + vs.joinToString(" ") { it.toString() })
        }
    }
}

class FileGregLogger(private val file: File): GregLogger {
    override var level = GregLogger.Level.LOW_DEBUG
    override fun sendLog(level: GregLogger.Level, source: String, vararg vs: Any?) {
        file.appendText("[${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)} $level][$source]: "
                + vs.joinToString(" ") { it.toString() } + "\n")
    }
}

class CombinedLogger: GregLogger {
    private val loggers = mutableListOf<GregLogger>()

    operator fun plusAssign(l: GregLogger) {
        loggers += l
    }

    override var level: GregLogger.Level
        get() = loggers.minOfOrNull { it.level } ?: GregLogger.Level.WARNING
        set(value) {
            loggers.forEach {
                it.level = value
            }
        }

    override fun sendLog(level: GregLogger.Level, source: String, vararg vs: Any?) = loggers.forEach {
        it.sendLog(level, source, *vs)
    }

}