package gregc.gregchess

import java.util.logging.Logger

enum class GregLevel {
    LOW_DEBUG, MID_DEBUG, HIGH_DEBUG, IO_DEBUG, WARNING
}

class GregLogger(private val logger: Logger) {
    var level: GregLevel = GregLevel.WARNING
    fun log(level: GregLevel, vararg vs: Any?) {
        if (level >= GregLevel.WARNING)
            logger.warning(vs.joinToString(" ") { it.toString() })
        else if (this.level <= level) {
            logger.info(vs.joinToString(" ") { it.toString() })
        }
    }
    fun low(vararg vs: Any?) = log(GregLevel.LOW_DEBUG, *vs)
    fun mid(vararg vs: Any?) = log(GregLevel.MID_DEBUG, *vs)
    fun high(vararg vs: Any?) = log(GregLevel.HIGH_DEBUG, *vs)
    fun io(vararg vs: Any?) = log(GregLevel.IO_DEBUG, *vs)
    fun warn(vararg vs: Any?) = log(GregLevel.WARNING, *vs)
}