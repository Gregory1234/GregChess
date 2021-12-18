package gregc.gregchess.registry

import gregc.gregchess.ChessModule
import gregc.gregchess.chess.ChessFlag
import gregc.gregchess.chess.EndReason
import gregc.gregchess.chess.component.Component
import gregc.gregchess.chess.component.ComponentData
import gregc.gregchess.chess.move.MoveNameTokenType
import gregc.gregchess.chess.move.MoveTrait
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.chess.player.ChessPlayerType
import gregc.gregchess.chess.variant.ChessVariant
import gregc.gregchess.isValidId
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass

class RegistryValidationException(val module: ChessModule, val type: Registry<*, *, *>, val text: String)
    : IllegalStateException("$module:${type.name}: $text")
class RegistryKeyValidationException(
    val module: ChessModule, val type: Registry<*, *, *>, val key: Any?, val value: Any?, val text: String
) : IllegalArgumentException("$module:${type.name}: $text: $key - $value")

private inline fun <K, T, B : RegistryBlock<K, T>> B.requireValid(type: Registry<K, T, B>, condition: Boolean, message: () -> String) {
    if (!condition)
        throw RegistryValidationException(module, type, message())
}

private inline fun <K, T, B : RegistryBlock<K, T>> B.requireValidKey(
    type: Registry<K, T, B>, key: K, value: T, condition: Boolean, message: () -> String
) {
    if (!condition)
        throw RegistryKeyValidationException(module, type, key, value, message())
}

abstract class RegistryBlock<K, T>(val module: ChessModule) : FiniteRegistryBlockView<K, T> {
    abstract fun set(key: K, value: T)
    abstract fun validate()
}

abstract class Registry<K, T, B : RegistryBlock<K, T>>(val name: String) : FiniteRegistryView<K, T> {
    init {
        @Suppress("LeakingThis")
        REGISTRIES += this
    }

    abstract operator fun get(module: ChessModule): B

    final override fun get(module: ChessModule, key: K): T = get(module)[key]
    final override fun getOrNull(module: ChessModule, key: K): T? = get(module).getOrNull(key)
    final override fun get(key: RegistryKey<K>): T = get(key.module, key.key)
    final override fun getOrNull(key: RegistryKey<K>): T? = getOrNull(key.module, key.key)
    final override fun keysOf(module: ChessModule): Set<K> = get(module).keys
    override fun valuesOf(module: ChessModule): Collection<T> = get(module).values

    operator fun set(key: RegistryKey<K>, value: T) = set(key.module, key.key, value)
    operator fun set(module: ChessModule, key: K, value: T) = get(module).set(key, value)

    companion object {
        @JvmField
        val REGISTRIES = mutableListOf<Registry<*,*,*>>()
        @JvmField
        val PIECE_TYPE = NameRegistry<PieceType>("piece_type")
        @JvmField
        val END_REASON = NameRegistry<EndReason<*>>("end_reason")
        @JvmField
        val VARIANT = NameRegistry<ChessVariant>("variant")
        @JvmField
        val FLAG = NameRegistry<ChessFlag>("flag")
        @JvmField
        val MOVE_NAME_TOKEN_TYPE = NameRegistry<MoveNameTokenType<*>>("move_name_token_type")
        @JvmField
        val COMPONENT_CLASS = NameRegistry<KClass<out Component>>("component_class")
        val COMPONENT_DATA_CLASS = ConnectedRegistry<KClass<out Component>, KClass<out ComponentData<*>>>(
            "component_data_class", COMPONENT_CLASS
        )
        @JvmField
        val COMPONENT_SERIALIZER = ConnectedRegistry<KClass<out Component>, KSerializer<out ComponentData<*>>>(
            "component_serializer", COMPONENT_CLASS
        )
        @JvmField
        val MOVE_TRAIT_CLASS = NameRegistry<KClass<out MoveTrait>>("move_trait_class")
        @JvmField
        val PLAYER_TYPE = NameRegistry<ChessPlayerType<*>>("player_type")
        @JvmField
        val PLAYER_TYPE_CLASS = ConnectedBiRegistry<ChessPlayerType<*>, KClass<*>>("player_type_class", PLAYER_TYPE)
    }
}

abstract class BiRegistryBlock<K, T>(module: ChessModule) : RegistryBlock<K, T>(module), FiniteBiRegistryBlockView<K, T>

abstract class BiRegistry<K, T, B : BiRegistryBlock<K, T>>(name: String) : Registry<K, T, B>(name), FiniteBiRegistryView<K, T> {
    final override fun valuesOf(module: ChessModule): Set<T> = get(module).values
}

class NameRegistry<T>(name: String) : BiRegistry<String, T, NameRegistry<T>.Block>(name) {
    private val valueEntries = mutableMapOf<T, RegistryKey<String>>()
    private val blocks = mutableMapOf<ChessModule, Block>()

    inner class Block(module: ChessModule) : BiRegistryBlock<String, T>(module) {
        private val members = mutableMapOf<String, T>()
        private val names = mutableMapOf<T, String>()

        override val keys: Set<String> get() = members.keys
        override val values: Set<T> get() = names.keys

        override fun set(key: String, value: T) {
            requireValidKey(this@NameRegistry, key, value, !module.locked) { "Module is locked" }
            requireValidKey(this@NameRegistry, key, value, key.isValidId()) { "Key is invalid" }
            requireValidKey(this@NameRegistry, key, value, key !in members) { "Key is already registered" }
            requireValidKey(this@NameRegistry, key, value, value !in valueEntries) { "Value is already registered" }
            members[key] = value
            names[value] = key
            valueEntries[value] = RegistryKey(module, key)
        }

        override fun getValueOrNull(key: String): T? = members[key]

        override fun getKeyOrNull(value: T): String? = names[value]

        override fun validate() {}
    }

    override fun get(module: ChessModule): Block = blocks.getOrPut(module) { Block(module) }

    override val keys: Set<RegistryKey<String>> get() = valueEntries.values.toSet()
    override val values: Set<T> get() = valueEntries.keys

    override fun getOrNull(value: T): RegistryKey<String>? = valueEntries[value]
}

class ConnectedRegistry<K, T>(name: String, val base: FiniteRegistryView<*, K>) : Registry<K, T, ConnectedRegistry<K, T>.Block>(name) {
    private val blocks = mutableMapOf<ChessModule, Block>()

    inner class Block(module: ChessModule) : RegistryBlock<K, T>(module) {
        private val members = mutableMapOf<K, T>()

        override val keys: Set<K> = members.keys
        override val values: Collection<T> = members.values

        override fun set(key: K, value: T) {
            requireValidKey(this@ConnectedRegistry, key, value, !module.locked) { "Module is locked" }
            requireValidKey(this@ConnectedRegistry, key, value, key in base.valuesOf(module)) { "Key is invalid" }
            requireValidKey(this@ConnectedRegistry, key, value, key !in members) { "Key is already registered" }
            members[key] = value
        }

        override fun validate() = requireValid(this@ConnectedRegistry, base.valuesOf(module).all { it in members }) { "Registry incomplete" }

        override fun getValueOrNull(key: K): T? = members[key]

    }

    override fun get(module: ChessModule): Block = blocks.getOrPut(module) { Block(module) }

    override val keys: Set<RegistryKey<K>>
        get() = blocks.flatMap { b -> b.value.keys.map { RegistryKey(b.key, it) } }.toSet()
    override val values: Collection<T>
        get() = blocks.flatMap { b -> b.value.values }

}

class ConnectedBiRegistry<K, T>(name: String, val base: FiniteRegistryView<*, K>) : BiRegistry<K, T, ConnectedBiRegistry<K, T>.Block>(name) {
    private val valueEntries = mutableMapOf<T, RegistryKey<K>>()
    private val blocks = mutableMapOf<ChessModule, Block>()

    inner class Block(module: ChessModule) : BiRegistryBlock<K, T>(module) {
        private val members = mutableMapOf<K, T>()
        private val reversed = mutableMapOf<T, K>()

        override val keys: Set<K> = members.keys
        override val values: Set<T> = reversed.keys

        override fun set(key: K, value: T) {
            requireValidKey(this@ConnectedBiRegistry, key, value, !module.locked) { "Module is locked" }
            requireValidKey(this@ConnectedBiRegistry, key, value, key in base.valuesOf(module)) { "Key is invalid" }
            requireValidKey(this@ConnectedBiRegistry, key, value, key !in members) { "Key is already registered" }
            requireValidKey(this@ConnectedBiRegistry, key, value, value !in valueEntries) { "Value is already registered" }
            members[key] = value
            reversed[value] = key
            valueEntries[value] = RegistryKey(module, key)
        }

        override fun validate() = requireValid(this@ConnectedBiRegistry, base.valuesOf(module).all { it in members }) { "Registry incomplete" }

        override fun getValueOrNull(key: K): T? = members[key]
        override fun getKeyOrNull(value: T): K? = reversed[value]

    }

    override fun get(module: ChessModule): Block = blocks.getOrPut(module) { Block(module) }

    override val keys: Set<RegistryKey<K>> get() = valueEntries.values.toSet()
    override val values: Set<T> get() = valueEntries.keys

    override fun getOrNull(value: T): RegistryKey<K>? = valueEntries[value]

}