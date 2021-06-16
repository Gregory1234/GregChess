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
    fun getOrNull(path: String): View?
    operator fun get(path: String): View
    fun fullPath(path: String): String
}

val View.childrenViews: Map<String, View>? get() = children?.associateWith { this[it] }

fun <T> View.getVal(path: String, type: String, default: T, warnMissing: Boolean = true, parser: (String) -> T?): T =
    getPureString(path).getVal(fullPath(path), type, default, warnMissing, parser)
fun <T> View.getList(path: String, type: String, warnMissing: Boolean = true, parser: (String) -> T?): List<T> =
    getPureStringList(path).getList(fullPath(path), type, warnMissing, parser)

fun View.getString(path: String) = getVal(path, "string", fullPath(path), true, ::processString)
fun View.getOptionalString(path: String) = getVal(path, "string", null, false, ::processString)
fun View.getStringList(path: String) = getList(path, "string", true, ::processString)
fun View.getDefaultBoolean(path: String, def: Boolean, warnMissing: Boolean = false) = getVal(path, "duration", def, warnMissing, String::toBooleanStrictOrNull)
fun View.getDefaultInt(path: String, def: Int, warnMissing: Boolean = false) = getVal(path, "duration", def, warnMissing, String::toIntOrNull)
fun View.getDuration(path: String) = getVal(path, "duration", 0.seconds, true, ::parseDuration)
fun View.getOptionalDuration(path: String) = getVal(path, "duration", null, false, ::parseDuration)
fun View.getChar(path: String) = getVal(path, "char", ' ', true) { if (it.length == 1) it[0] else null }
fun View.getTimeFormat(path: String) = getVal(path, "time format", TimeFormat(""), true, ::TimeFormat)
fun View.getStringFormat(path: String, vararg vs: Any?) = getVal(path, "string format", fullPath(path), true) {
    numberedFormat(it, *vs)?.let(::processString)
}
fun <T1> View.getStringFormatF1(path: String): (T1) -> String = getVal(path, "string format", { _ -> fullPath(path) }, true) {
    if (!numberedFormatCheck(it, 1u))
        null
    else { a1 ->
        processString(numberedFormat(it, a1)!!)
    }
}
fun <T1, T2> View.getStringFormatF2(path: String): (T1, T2) -> String = getVal(path, "string format", { _, _ -> fullPath(path) }, true) {
    if (!numberedFormatCheck(it, 2u))
        null
    else { a1, a2 ->
        processString(numberedFormat(it, a1, a2)!!)
    }
}
fun <T: Enum<T>> View.getEnum(path: String, def: T, warnMissing: Boolean = true) = getVal(path, def::class.simpleName ?: "enum", def, warnMissing, enumValueOrNull(def::class))
fun <T: Enum<T>> View.getEnumList(path: String, cl: KClass<T>) = getList(path, cl.simpleName ?: "enum", true, enumValueOrNull(cl))
inline fun <reified T: Enum<T>> View.getEnumList(path: String) = getEnumList(path, T::class)