package gregc.gregchess

import gregc.gregchess.chess.component.ChessClock
import gregc.gregchess.chess.component.ComponentConfig
import java.util.*
import kotlin.reflect.KClass

infix fun String.addDot(other: String) = if (isNotEmpty() && other.isNotEmpty()) "$this.$other" else "$this$other"

class View(private val root: String) {

    private val file = GregChess.plugin.config

    private val children: Set<String>?
        get() = file.getConfigurationSection(root)?.getKeys(false)

    operator fun get(path: String): View = View(fullPath(path))

    fun fullPath(path: String): String = root addDot path

    val childrenViews: Map<String, View>?
        get() = children?.associateWith { this[it] }

    fun <T> getVal(path: String, type: String, default: T, warnMissing: Boolean = true, parser: (String) -> T?): T {
        val str = file.getString(fullPath(path))
        if (str == null) {
            if (warnMissing) {
                glog.warn("Not found $type ${fullPath(path)}, defaulted to $default!")
            }
            return default
        }
        val ret = parser(str)
        if (ret == null) {
            glog.warn("${type.upperFirst()} ${fullPath(path)} is in a wrong format, defaulted to $default!")
            return default
        }
        return ret
    }
    fun <T> getList(path: String, type: String, warnMissing: Boolean = true, parser: (String) -> T?): List<T> {
        if (fullPath(path) !in file) {
            if (warnMissing)
                glog.warn("Not found list of $type ${fullPath(path)}, defaulted to an empty list!")
            return emptyList()
        }
        val str = file.getStringList(fullPath(path))
        return str.mapNotNull {
            val ret = parser(it)
            if (ret == null) {
                glog.warn("${type.lowerFirst()} ${fullPath(path)} is in a wrong format, ignored!")
                null
            } else
                ret
        }
    }
}

fun View.getString(path: String) = getVal(path, "string", fullPath(path), true, String::chatColor)
fun View.getOptionalString(path: String) = getVal(path, "string", null, false, String::chatColor)
fun View.getStringList(path: String) = getList(path, "string", true, String::chatColor)
fun View.getDefaultBoolean(path: String, def: Boolean, warnMissing: Boolean = false) = getVal(path, "duration", def, warnMissing, String::toBooleanStrictOrNull)
fun View.getDefaultInt(path: String, def: Int, warnMissing: Boolean = false) = getVal(path, "duration", def, warnMissing, String::toIntOrNull)
fun View.getDuration(path: String) = getVal(path, "duration", 0.seconds, true, String::asDurationOrNull)
fun View.getOptionalDuration(path: String) = getVal(path, "duration", null, false, String::asDurationOrNull)
fun View.getChar(path: String) = getVal(path, "char", ' ', true) { if (it.length == 1) it[0] else null }

private fun <T: Enum<T>> parseEnum(cl: KClass<T>, s: String): T? = try {
    java.lang.Enum.valueOf(cl.java,s.uppercase())
} catch (e: IllegalArgumentException) {
    null
}

fun <T: Enum<T>> View.getEnum(path: String, cl: KClass<T>, def: T, warnMissing: Boolean = true) =
    getVal(path, cl.simpleName ?: "enum", def, warnMissing) { parseEnum(cl, it)  }
inline fun <reified T: Enum<T>> View.getEnum(path: String, def: T, warnMissing: Boolean = true) = getEnum(path, T::class, def, warnMissing)
fun <T: Enum<T>> View.getEnumList(path: String, cl: KClass<T>, warnMissing: Boolean = true) =
    getList(path, cl.simpleName ?: "enum", warnMissing) { parseEnum(cl, it) }
inline fun <reified T: Enum<T>> View.getEnumList(path: String, warnMissing: Boolean = true) = getEnumList(path, T::class, warnMissing)

@JvmField
val config: View = View("")

private fun String.formatOrNull(vararg args: Any?): String? = try {
    format(*args)
} catch (e: IllegalFormatException) {
    null
}

class LocalizedString(private val view: View, private val path: String, private vararg val args: Any?) {
    fun get(lang: String): String =
        view.getVal(path, "string", lang + "/" + view.fullPath(path), true) { s ->
            val f = s.formatOrNull(*args.map { if (it is LocalizedString) it.get(lang) else it }.toTypedArray())
            f?.chatColor()
        }
}

fun ComponentConfig.initBukkit() {
    this[ChessClock::class] = BukkitClockConfig
}

object BukkitClockConfig: ChessClock.Config {
    override val timeFormat: String
        get() = config.getString("Clock.TimeFormat")
}