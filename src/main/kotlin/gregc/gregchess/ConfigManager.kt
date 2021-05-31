package gregc.gregchess

object ConfigManager : View("")

open class View protected constructor(private val rootPath: String = "") {
    private val config
        get() = GregInfo.plugin.config
    private val section
        get() = config.getConfigurationSection(rootPath)

    protected fun childPath(path: String) = (if (rootPath == "") path else ("$rootPath.$path"))

    fun getView(path: String) = View(childPath(path))

    val children: Map<String, View>
        get() = section?.getKeys(false)?.mapNotNull {
            val view = getView(it)
            if (view.exists) Pair(it, view)
            else null
        }.orEmpty().toMap()

    val exists
        get() = section != null

    fun <T> get(
        path: String, type: String, default: T, warnMissing: Boolean = true, parser: (String) -> T?
    ): T {
        val fullPath = childPath(path)
        val str = config.getString(fullPath)
        if (str == null) {
            if (warnMissing) {
                glog.warn("Not found $type $fullPath, defaulted to $default!", Thread.currentThread().stackTrace.joinToString("\n"))
            }
            return default
        }
        val ret = parser(str)
        if (ret == null) {
            glog.warn("${type.upperFirst()} $fullPath is in a wrong format, defaulted to $default!", Thread.currentThread().stackTrace.joinToString("\n"))
            return default
        }
        return ret
    }

    fun <T> getList(
        path: String, type: String, warnMissing: Boolean = true, parser: (String) -> T?
    ): List<T> {
        val fullPath = childPath(path)
        if (fullPath !in config) {
            if (warnMissing)
                glog.warn("Not found list of $type $fullPath, defaulted to an empty list!", Thread.currentThread().stackTrace.joinToString("\n"))
            return emptyList()
        }
        val str = config.getStringList(fullPath)
        return str.mapNotNull {
            val ret = parser(it)
            if (ret == null) {
                glog.warn("${type.lowerFirst()} $fullPath is in a wrong format, ignored!", Thread.currentThread().stackTrace.joinToString("\n"))
                null
            } else
                ret
        }

    }

    fun getString(path: String) = get(path, "string", path) { chatColor(it) }

    fun getChar(path: String) = get(path, "char", ' ') { if (it.length == 1) it[0] else null }

    fun getFormatString(path: String, vararg args: Any?) =
        get(path, "format string", path) {
            numberedFormat(it, *args)?.let(::chatColor)
        }

    fun getDuration(path: String, warnMissing: Boolean = true) =
        get(path, "duration", 0.seconds, warnMissing, ::parseDuration)

    fun getError(name: String) = get("Message.Error.$name", "error", name) { chatColor(it) }

    inline fun <reified T : Enum<T>> getEnum(
        path: String,
        default: T,
        warnMissing: Boolean = true
    ) =
        get(path, T::class.simpleName?.lowerFirst() ?: "enum", default, warnMissing) {
            try {
                enumValueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

    inline fun <reified T : Enum<T>> getEnumList(
        path: String,
        warnMissing: Boolean = true
    ) =
        getList(path, T::class.simpleName?.lowerFirst() ?: "enum", warnMissing) {
            try {
                enumValueOf<T>(it.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

    fun getOptionalString(path: String): String? =
        get(path, "optional string", null, false) { chatColor(it) }

    fun getOptionalDuration(path: String) = get(path, "duration", null, false, ::parseDuration)

    fun getBool(path: String, default: Boolean, warnMissing: Boolean = false) =
        get(path, "boolean", default, warnMissing) { it.toBooleanStrictOrNull() }

    fun getStringList(path: String) = getList(path, "string") { chatColor(it) }

    fun getInt(path: String, default: Int = 0, warnMissing: Boolean = true): Int =
        get(path, "int", default, warnMissing) { it.toIntOrNull() }
}