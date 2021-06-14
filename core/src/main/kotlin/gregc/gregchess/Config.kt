package gregc.gregchess

import kotlin.reflect.*

infix fun String.addDot(other: String) = if (isNotEmpty() && other.isNotEmpty()) "$this.$other" else "$this$other"

fun interface ConfigVal<T> {
    fun get(): T
}

fun <R: ConfigBlock, T> KProperty1<R, T>.toPath(cl: KClass<R>) = ConfigVal { this.get(Config.get(cl)!!) }

val <reified R: ConfigBlock, T> KProperty1<R, T>.path inline get() = toPath(R::class)

inline operator fun <reified R: ConfigBlock, T> KProperty1<R, T>.getValue(owner: Any?, property: KProperty<*>) = path.get()
operator fun <T> ConfigVal<T>.getValue(owner: Any?, property: KProperty<*>) = get()

interface ConfigBlock

object Config {
    private val blocks = mutableListOf<ConfigBlock>()

    operator fun plusAssign(b: ConfigBlock) {
        blocks += b
    }

    fun <T: ConfigBlock> get(cl: KClass<T>) = blocks.filterIsInstance(cl.java).firstOrNull()

    inline fun <reified T: ConfigBlock> get() = get(T::class)

    inline operator fun <reified T: ConfigBlock> getValue(owner: Config, property: KProperty<*>): T = get()!!
}

fun <T> String?.getVal(path: String, type: String, default: T, warnMissing: Boolean = true, parser: (String) -> T?): T {
    if (this == null) {
        if (warnMissing) {
            glog.warn("Not found $type $path, defaulted to $default!")
        }
        return default
    }
    val ret = parser(this)
    if (ret == null) {
        glog.warn("${type.upperFirst()} $path is in a wrong format, defaulted to $default!")
        return default
    }
    return ret
}

fun <T> List<String>?.getList(path: String, type: String, warnMissing: Boolean = true, parser: (String) -> T?): List<T> {
    if (this == null) {
        if (warnMissing)
            glog.warn("Not found list of $type $path, defaulted to an empty list!")
        return emptyList()
    }
    return mapNotNull {
        val ret = parser(it)
        if (ret == null) {
            glog.warn("${type.lowerFirst()} $path is in a wrong format, ignored!")
            null
        } else
            ret
    }

}

interface View {
    fun getPureString(path: String): String?
    fun getPureStringList(path: String): List<String>?
    fun processString(s: String): String
    val children: Set<String>?
    operator fun get(path: String): View?
}

fun <T> View.getVal(path: String, type: String, default: T, warnMissing: Boolean = true, parser: (String) -> T?): T =
    getPureString(path).getVal(path, type, default, warnMissing, parser)
fun <T> View.getList(path: String, type: String, warnMissing: Boolean = true, parser: (String) -> T?): List<T> =
    getPureStringList(path).getList(path, type, warnMissing, parser)

fun View.getString(path: String) = getVal(path, "string", path, true, ::processString)
fun View.getDefaultBoolean(path: String, def: Boolean) = getVal(path, "duration", def, true, String::toBooleanStrictOrNull)
fun View.getDefaultInt(path: String, def: Int) = getVal(path, "duration", def, true, String::toIntOrNull)
fun View.getDuration(path: String) = getVal(path, "duration", 0.seconds, true, ::parseDuration)
fun View.getTimeFormat(path: String) = getVal(path, "time format", TimeFormat(""), true, ::TimeFormat)
fun View.getStringFormat(path: String, vararg vs: Any?) = getVal(path, "string format", path, true) {
    numberedFormat(it, *vs)?.let(::processString)
}
fun <T: Enum<T>> View.getEnum(path: String, def: T) = getVal(path, def::class.simpleName ?: "enum", def, true, enumValueOrNull(def::class))