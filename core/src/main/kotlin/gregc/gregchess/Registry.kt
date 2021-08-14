package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentData
import gregc.gregchess.chess.variant.ChessVariant
import kotlin.reflect.KClass

class RegistryType<K, T>(val name: String, val base: RegistryType<*, K>? = null) {
    internal val valueModule = mutableMapOf<T, ChessModule>()
    internal val valueKey = mutableMapOf<T, K>()

    fun getModule(v: T): ChessModule = valueModule[v]!!
    fun getModuleOrNull(v: T): ChessModule? = valueModule[v]
    operator fun get(v: T): K = valueKey[v]!!
    fun getOrNull(v: T): K? = valueKey[v]

    init {
        require(name.isValidId())
    }

    companion object {
        @JvmField
        val PIECE_TYPE = RegistryType<String, PieceType>("piece_type")
        @JvmField
        val END_REASON = RegistryType<String, EndReason<*>>("end_reason")
        @JvmField
        val VARIANT = RegistryType<String, ChessVariant>("variant")
        @JvmField
        val FLAG_TYPE = RegistryType<String, ChessFlagType>("flag_type")
        @JvmField
        val MOVE_NAME_TOKEN_TYPE = RegistryType<String, MoveNameTokenType<*>>("move_name_token_type")
        @JvmField
        val COMPONENT_CLASS = RegistryType<String, KClass<out Component>>("component_class")
        @JvmField
        val COMPONENT_DATA_CLASS = RegistryType<KClass<out Component>, KClass<out ComponentData<*>>>("component_data_class", COMPONENT_CLASS)
    }
}

class Registry<K, T>(private val module: ChessModule, val type: RegistryType<K, T>) {

    private val members = mutableMapOf<K, T>()
    private val reversed = mutableMapOf<T, K>()

    operator fun set(key: K, v: T) {
        if (key is String)
            require(key.isValidId())
        if (type.base != null)
            require(key in module[type.base].values)
        require(key !in members)
        require(v !in type.valueModule)
        require(v !in type.valueKey)
        members[key] = v
        reversed[v] = key
        type.valueModule[v] = module
        type.valueKey[v] = key
    }

    operator fun get(key: K) = members[key]!!
    fun getOrNull(key: K) = members[key]
    @JvmName("getKey")
    operator fun get(v: T) = reversed[v]!!
    @JvmName("getKeyOrNull")
    fun getOrNull(v: T) = reversed[v]

    val keys: Set<K> get() = members.keys
    val values: Set<T> get() = reversed.keys
}