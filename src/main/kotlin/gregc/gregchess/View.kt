package gregc.gregchess

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

    private val exists
        get() = section != null

    fun <T> get(path: String, type: String, default: T, warnMissing: Boolean = true, parser: (String) -> T?): T {
        val fullPath = childPath(path)
        val str = config.getString(fullPath)
        if (str == null) {
            if (warnMissing) {
                glog.warn("Not found $type $fullPath, defaulted to $default!")
            }
            return default
        }
        val ret = parser(str)
        if (ret == null) {
            glog.warn("${type.upperFirst()} $fullPath is in a wrong format, defaulted to $default!")
            return default
        }
        return ret
    }

    fun <T> getList(path: String, type: String, warnMissing: Boolean = true, parser: (String) -> T?): List<T> {
        val fullPath = childPath(path)
        if (fullPath !in config) {
            if (warnMissing)
                glog.warn("Not found list of $type $fullPath, defaulted to an empty list!")
            return emptyList()
        }
        val str = config.getStringList(fullPath)
        return str.mapNotNull {
            val ret = parser(it)
            if (ret == null) {
                glog.warn("${type.lowerFirst()} $fullPath is in a wrong format, ignored!")
                null
            } else
                ret
        }

    }

    inline fun <reified T : Enum<T>> getEnum(path: String, default: T, warnMissing: Boolean = true) =
        get(path, T::class.simpleName?.lowerFirst() ?: "enum", default, warnMissing) {
            try {
                enumValueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

    inline fun <reified T : Enum<T>> getEnumList(path: String,warnMissing: Boolean = true) =
        getList(path, T::class.simpleName?.lowerFirst() ?: "enum", warnMissing) {
            try {
                enumValueOf<T>(it.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

    fun getFormatString(path: String, vararg args: Any?) = get(path, "format string", path) {
        numberedFormat(it, *args)?.let(::chatColor)
    }
}