package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentData
import gregc.gregchess.chess.variant.ChessVariant
import kotlin.reflect.KClass

abstract class RegistryType<K, T, R : Registry<K, T, R>>(val name: String) {
    abstract fun createRegistry(module: ChessModule): R

    companion object {
        @JvmField
        val PIECE_TYPE = NameRegistryType<PieceType>("piece_type")
        @JvmField
        val END_REASON = NameRegistryType<EndReason<*>>("end_reason")
        @JvmField
        val VARIANT = NameRegistryType<ChessVariant>("variant")
        @JvmField
        val FLAG_TYPE = NameRegistryType<ChessFlagType>("flag_type")
        @JvmField
        val MOVE_NAME_TOKEN_TYPE = NameRegistryType<MoveNameTokenType<*>>("move_name_token_type")
        @JvmField
        val COMPONENT_CLASS = NameRegistryType<KClass<out Component>>("component_class")
        @JvmField
        val COMPONENT_DATA_CLASS = ConnectedRegistryType<KClass<out Component>, KClass<out ComponentData<*>>>("component_data_class", COMPONENT_CLASS)
    }
}

abstract class Registry<K, T, R : Registry<K, T, R>>(val module: ChessModule) {
    abstract operator fun set(key: K, value: T)

    abstract fun getValueOrNull(key: K): T?
    fun getOrNull(key: K): T? = getValueOrNull(key)
    fun getValue(key: K): T = getValueOrNull(key)!!
    operator fun get(key: K): T = getOrNull(key)!!

    open fun validate() {}

    abstract val keys: Set<K>
    abstract val values: Collection<T>
}

abstract class DoubleRegistryType<K, T, R: DoubleRegistry<K, T, R>>(name: String): RegistryType<K, T, R>(name) {
    abstract fun getModuleOrNull(value: T): ChessModule?
    abstract fun getOrNull(value: T): K?
    fun getModule(value: T): ChessModule = getModuleOrNull(value)!!
    operator fun get(value: T): K = getOrNull(value)!!
}

abstract class DoubleRegistry<K, T, R: DoubleRegistry<K, T, R>>(module: ChessModule): Registry<K, T, R>(module) {
    abstract fun getKeyOrNull(value: T): K?
    @JvmName("getKeyOrNullKt")
    @JvmSynthetic
    fun getOrNull(value: T): K? = getKeyOrNull(value)
    fun getKey(value: T): K = getKeyOrNull(value)!!
    @JvmName("getKeyKt")
    @JvmSynthetic
    operator fun get(value: T): K = getOrNull(value)!!
    abstract override val values: Set<T>
}

class NameRegistryType<T>(name: String): DoubleRegistryType<String, T, NameRegistry<T>>(name) {
    internal val valueModule = mutableMapOf<T, ChessModule>()
    internal val valueName = mutableMapOf<T, String>()

    override fun createRegistry(module: ChessModule) = NameRegistry(module, this)

    override fun getOrNull(value: T): String? = valueName[value]
    override fun getModuleOrNull(value: T): ChessModule? = valueModule[value]
}

class NameRegistry<T>(module: ChessModule, val type: NameRegistryType<T>): DoubleRegistry<String, T, NameRegistry<T>>(module) {
    private val members = mutableMapOf<String, T>()
    private val names = mutableMapOf<T, String>()

    override fun set(key: String, value: T) {
        require(key.isValidId())
        require(key !in members)
        require(value !in type.valueName)
        require(value !in type.valueModule)
        members[key] = value
        names[value] = key
        type.valueModule[value] = module
        type.valueName[value] = key
    }

    override fun getValueOrNull(key: String): T? = members[key]
    override fun getKeyOrNull(value: T): String? = names[value]

    override val keys: Set<String> get() = members.keys
    override val values: Set<T> get() = names.keys
}

class ConnectedRegistryType<K, T>(name: String, val base: DoubleRegistryType<*, K, *>): DoubleRegistryType<K, T, ConnectedRegistry<K, T>>(name) {
    internal val valueModule = mutableMapOf<T, ChessModule>()
    internal val valueKey = mutableMapOf<T, K>()

    override fun createRegistry(module: ChessModule) = ConnectedRegistry(module, this)

    override fun getOrNull(value: T): K? = valueKey[value]
    override fun getModuleOrNull(value: T): ChessModule? = valueModule[value]
}

class ConnectedRegistry<K, T>(module: ChessModule, val type: ConnectedRegistryType<K, T>): DoubleRegistry<K, T, ConnectedRegistry<K, T>>(module) {
    private val members = mutableMapOf<K, T>()
    private val reversed = mutableMapOf<T, K>()

    override fun set(key: K, value: T) {
        require(key in module[type.base].values)
        require(key !in members)
        require(value !in type.valueKey)
        require(value !in type.valueModule)
        members[key] = value
        reversed[value] = key
        type.valueModule[value] = module
        type.valueKey[value] = key
    }

    override fun getValueOrNull(key: K): T? = members[key]
    override fun getKeyOrNull(value: T): K? = reversed[value]

    override fun validate() = require(module[type.base].values.all { it in members })

    override val keys: Set<K> get() = members.keys
    override val values: Set<T> get() = reversed.keys
}

class SingleConnectedRegistryType<K, T>(name: String, val base: DoubleRegistryType<*, K, *>): RegistryType<K, T, SingleConnectedRegistry<K, T>>(name) {

    override fun createRegistry(module: ChessModule) = SingleConnectedRegistry(module, this)
}

class SingleConnectedRegistry<K, T>(module: ChessModule, val type: SingleConnectedRegistryType<K, T>): Registry<K, T, SingleConnectedRegistry<K, T>>(module) {
    private val members = mutableMapOf<K, T>()

    override fun set(key: K, value: T) {
        require(key in module[type.base].values)
        require(key !in members)
        members[key] = value
    }

    override fun getValueOrNull(key: K): T? = members[key]

    override fun validate() = require(module[type.base].values.all { it in members })

    override val keys: Set<K> get() = members.keys
    override val values: Collection<T> get() = members.values
}