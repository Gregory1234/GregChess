package gregc.gregchess

import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentData
import gregc.gregchess.chess.variant.ChessVariant
import kotlin.reflect.KClass

interface RegistryView<K, T> {
    operator fun get(key: RegistryKey<K>): T = getOrNull(key)!!
    fun getOrNull(key: RegistryKey<K>): T?
    operator fun get(namespace: String, key: K): T = get(RegistryKey(namespace, key))
    fun getOrNull(namespace: String, key: K): T? = ChessModule.getOrNull(namespace)?.let { getOrNull(it, key) }
    operator fun get(module: ChessModule, key: K): T = get(RegistryKey(module, key))
    fun getOrNull(module: ChessModule, key: K): T? = getOrNull(RegistryKey(module, key))
}

interface EnumeratedRegistryView<K, T> : RegistryView<K, T> {
    val keys: Set<RegistryKey<K>>
    val values: Collection<T>
    fun valuesOf(module: ChessModule): Collection<T>
}

data class RegistryKey<K>(val module: ChessModule, val key: K) {
    constructor(namespace: String, key: K): this(ChessModule[namespace], key)

    override fun toString() = "${module.namespace}:$key"
}

fun String.toKey(): RegistryKey<String> {
    val sections = split(":")
    return when(sections.size) {
        1 -> RegistryKey(GregChessModule, this)
        2 -> RegistryKey(sections[0], sections[1])
        else -> throw IllegalArgumentException(this)
    }
}

abstract class RegistryType<K, T, R : Registry<K, T, R>>(val name: String): EnumeratedRegistryView<K, T> {
    abstract fun createRegistry(module: ChessModule): R
    final override operator fun get(key: RegistryKey<K>): T = key.module[this][key.key]
    final override fun getOrNull(key: RegistryKey<K>): T? = key.module[this].getOrNull(key.key)
    final override operator fun get(namespace: String, key: K): T = get(ChessModule[namespace], key)
    final override fun getOrNull(namespace: String, key: K): T? = ChessModule.getOrNull(namespace)?.let { getOrNull(it, key) }
    final override operator fun get(module: ChessModule, key: K): T = module[this][key]
    final override fun getOrNull(module: ChessModule, key: K): T? = module[this].getOrNull(key)

    operator fun set(key: RegistryKey<K>, value: T) = key.module[this].set(key.key, value)
    operator fun set(module: ChessModule, key: K, value: T) = module[this].set(key, value)

    override val keys: Set<RegistryKey<K>>
        get() = ChessModule.modules.flatMap { m -> m[this].keys.map { k -> RegistryKey(m, k) } }.toSet()
    override val values: Collection<T>
        get() = ChessModule.modules.flatMap { m -> m[this].values }

    override fun valuesOf(module: ChessModule): Collection<T> = module[this].values

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
        @JvmField
        val MOVE_TRAIT_CLASS = NameRegistryType<KClass<out MoveTrait>>("move_trait_class")
        @JvmField
        val PLAYER_TYPE = NameRegistryType<KClass<out ChessPlayerInfo>>("player_type")
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

interface DoubleRegistryView<K, T>: RegistryView<K, T> {
    operator fun get(value: T): RegistryKey<K> = getOrNull(value)!!
    fun getOrNull(value: T): RegistryKey<K>?
    fun getKey(value: T): K = getKeyOrNull(value)!!
    fun getKeyOrNull(value: T): K? = getOrNull(value)?.key
    fun getModule(value: T): ChessModule = getModuleOrNull(value)!!
    fun getModuleOrNull(value: T): ChessModule? = getOrNull(value)?.module
}

interface DoubleEnumeratedRegistryView<K, T>: DoubleRegistryView<K, T>, EnumeratedRegistryView<K, T> {
    override val values: Set<T>
    override fun valuesOf(module: ChessModule): Set<T>
}

abstract class DoubleRegistryType<K, T, R: DoubleRegistry<K, T, R>>(name: String): RegistryType<K, T, R>(name), DoubleEnumeratedRegistryView<K, T> {
    abstract override val values: Set<T>
    abstract override fun valuesOf(module: ChessModule): Set<T>
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
    internal val valueEntries = mutableMapOf<T, RegistryKey<String>>()

    override fun createRegistry(module: ChessModule) = NameRegistry(module, this)

    override fun getOrNull(value: T): RegistryKey<String>? = valueEntries[value]

    override val keys: Set<RegistryKey<String>> get() = valueEntries.values.toSet()
    override val values: Set<T> = valueEntries.keys
    override fun valuesOf(module: ChessModule): Set<T> = module[this].values
}

class NameRegistry<T>(module: ChessModule, val type: NameRegistryType<T>): DoubleRegistry<String, T, NameRegistry<T>>(module) {
    private val members = mutableMapOf<String, T>()
    private val names = mutableMapOf<T, String>()

    override fun set(key: String, value: T) {
        require(!module.locked)
        require(key.isValidId())
        require(key !in members)
        require(value !in type.valueEntries)
        members[key] = value
        names[value] = key
        type.valueEntries[value] = RegistryKey(module, key)
    }

    override fun getValueOrNull(key: String): T? = members[key]
    override fun getKeyOrNull(value: T): String? = names[value]

    override val keys: Set<String> get() = members.keys
    override val values: Set<T> get() = names.keys
}

class ConnectedRegistryType<K, T>(name: String, val base: DoubleEnumeratedRegistryView<*, K>): DoubleRegistryType<K, T, ConnectedRegistry<K, T>>(name) {
    internal val valueEntries = mutableMapOf<T, RegistryKey<K>>()

    override fun createRegistry(module: ChessModule) = ConnectedRegistry(module, this)

    override fun getOrNull(value: T): RegistryKey<K>? = valueEntries[value]

    override val keys: Set<RegistryKey<K>> get() = valueEntries.values.toSet()
    override val values: Set<T> = valueEntries.keys
    override fun valuesOf(module: ChessModule): Set<T> = module[this].values
}

class ConnectedRegistry<K, T>(module: ChessModule, val type: ConnectedRegistryType<K, T>): DoubleRegistry<K, T, ConnectedRegistry<K, T>>(module) {
    private val members = mutableMapOf<K, T>()
    private val reversed = mutableMapOf<T, K>()

    override fun set(key: K, value: T) {
        require(!module.locked)
        require(key in type.base.valuesOf(module))
        require(key !in members)
        require(value !in type.valueEntries)
        members[key] = value
        reversed[value] = key
        type.valueEntries[value] = RegistryKey(module, key)
    }

    override fun getValueOrNull(key: K): T? = members[key]
    override fun getKeyOrNull(value: T): K? = reversed[value]

    override fun validate() = require(type.base.valuesOf(module).all { it in members })

    override val keys: Set<K> get() = members.keys
    override val values: Set<T> get() = reversed.keys
}

class SingleConnectedRegistryType<K, T>(name: String, val base: DoubleRegistryType<*, K, *>): RegistryType<K, T, SingleConnectedRegistry<K, T>>(name) {

    override fun createRegistry(module: ChessModule) = SingleConnectedRegistry(module, this)
}

class SingleConnectedRegistry<K, T>(module: ChessModule, val type: SingleConnectedRegistryType<K, T>): Registry<K, T, SingleConnectedRegistry<K, T>>(module) {
    private val members = mutableMapOf<K, T>()

    override fun set(key: K, value: T) {
        require(!module.locked)
        require(key in module[type.base].values)
        require(key !in members)
        members[key] = value
    }

    override fun getValueOrNull(key: K): T? = members[key]

    override fun validate() = require(module[type.base].values.all { it in members })

    override val keys: Set<K> get() = members.keys
    override val values: Collection<T> get() = members.values
}

class ChainRegistryView<K, I, T>(val base: RegistryView<K, I>, val extension: RegistryView<I, T>): RegistryView<K, T> {
    override fun getOrNull(key: RegistryKey<K>): T? = base.getOrNull(key)?.let { extension.getOrNull(key.module, it) }
}

class DoubleChainRegistryView<K, I, T>(val base: DoubleRegistryView<K, I>, val extension: DoubleRegistryView<I, T>): DoubleRegistryView<K, T> {
    override fun getOrNull(key: RegistryKey<K>): T? = base.getOrNull(key)?.let { extension.getOrNull(key.module, it) }
    override fun getModuleOrNull(value: T): ChessModule? = extension.getModuleOrNull(value)
    override fun getOrNull(value: T): RegistryKey<K>? = extension.getKeyOrNull(value)?.let { base.getOrNull(it) }
}