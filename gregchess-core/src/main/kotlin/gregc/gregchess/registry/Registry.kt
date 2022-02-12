package gregc.gregchess.registry

import gregc.gregchess.ChessModule
import gregc.gregchess.chess.*
import gregc.gregchess.chess.component.ComponentType
import gregc.gregchess.chess.move.*
import gregc.gregchess.chess.piece.PieceType
import gregc.gregchess.chess.piece.PlacedPieceType
import gregc.gregchess.chess.player.ChessPlayerType
import gregc.gregchess.chess.variant.ChessVariant

private class RegistryValidationException(
    val module: ChessModule, val type: Registry<*, *, *>, val text: String
): IllegalStateException("$module:${type.name}: $text")

private class RegistryKeyValidationException(
    val module: ChessModule, val type: Registry<*, *, *>, val key: Any?, val value: Any?, val text: String
) : IllegalArgumentException("$module:${type.name}: $text: $key: $value")

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
        val COMPONENT_TYPE = NameRegistry<ComponentType<*>>("component_type")
        @JvmField
        val MOVE_TRAIT_TYPE = NameRegistry<MoveTraitType<*>>("move_trait_type")
        @JvmField
        val PLAYER_TYPE = NameRegistry<ChessPlayerType<*>>("player_type")
        @JvmField
        val PLACED_PIECE_TYPE = NameRegistry<PlacedPieceType<*>>("placed_piece_type")
        @JvmField
        val VARIANT_OPTION = NameRegistry<ChessVariantOption<*>>("variant_option")
        @JvmField
        val STAT = NameRegistry<ChessStat<*>>("stat")
    }
}

abstract class BiRegistryBlock<K, T>(module: ChessModule) : RegistryBlock<K, T>(module), FiniteBiRegistryBlockView<K, T>

abstract class BiRegistry<K, T, B : BiRegistryBlock<K, T>>(name: String) : Registry<K, T, B>(name), FiniteBiRegistryView<K, T> {
    final override fun valuesOf(module: ChessModule): Set<T> = get(module).values
}

private fun String.isValidId(): Boolean = all { it == '_' || it in ('a'..'z') }

class NameRegistry<T>(name: String) : BiRegistry<String, T, NameRegistry<T>.Block>(name) {
    private val valueEntries = mutableMapOf<T, RegistryKey<String>>()
    private val blocks = mutableMapOf<ChessModule, Block>()

    inner class Block internal constructor(module: ChessModule) : BiRegistryBlock<String, T>(module) {
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

    fun simpleElementToString(value: T): String = "${getOrNull(value)}@${value.hashCode().toString(16)}"
}

class ConnectedRegistry<K, T>(name: String, val base: FiniteBiRegistryView<*, K>) : Registry<K, T, ConnectedRegistry<K, T>.Block>(name), FiniteSplitRegistryView<K, T> {
    private val blocks = mutableMapOf<ChessModule, Block>()

    inner class Block internal constructor(module: ChessModule) : RegistryBlock<K, T>(module) {
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

        inline fun completeWith(default: (K) -> T) {
            for (v in base.valuesOf(module)) {
                if (v !in keys) {
                    set(v, default(v))
                }
            }
        }

    }

    override fun get(module: ChessModule): Block = blocks.getOrPut(module) { Block(module) }

    override fun getKeyModule(key: K): ChessModule = base[key].module

    override val simpleKeys: Set<K>
        get() = blocks.flatMap { b -> b.value.keys }.toSet()
    override val keys: Set<RegistryKey<K>>
        get() = blocks.flatMap { b -> b.value.keys.map { RegistryKey(b.key, it) } }.toSet()
    override val values: Collection<T>
        get() = blocks.flatMap { b -> b.value.values }

}


class ConnectedBiRegistry<K, T>(name: String, val base: FiniteBiRegistryView<*, K>) : BiRegistry<K, T, ConnectedBiRegistry<K, T>.Block>(name), FiniteSplitRegistryView<K, T> {
    private val valueEntries = mutableMapOf<T, RegistryKey<K>>()
    private val blocks = mutableMapOf<ChessModule, Block>()

    inner class Block internal constructor(module: ChessModule) : BiRegistryBlock<K, T>(module) {
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

    override fun getKeyModule(key: K): ChessModule = base[key].module

    override val simpleKeys: Set<K> get() = valueEntries.values.map { it.key }.toSet()
    override val keys: Set<RegistryKey<K>> get() = valueEntries.values.toSet()
    override val values: Set<T> get() = valueEntries.keys

    override fun getOrNull(value: T): RegistryKey<K>? = valueEntries[value]

}

class ConnectedSetRegistry<E>(name: String, val base: FiniteBiRegistryView<*, E>) : Registry<E, Unit, ConnectedSetRegistry<E>.Block>(name), FiniteSplitRegistryView<E, Unit> {
    private val blocks = mutableMapOf<ChessModule, Block>()

    inner class Block internal constructor(module: ChessModule) : RegistryBlock<E, Unit>(module) {
        private val members = mutableSetOf<E>()
        override val keys: Set<E> get() = members
        override val values: Collection<Unit> = members.map { }

        override fun set(key: E, value: Unit) {
            requireValidKey(this@ConnectedSetRegistry, key, value, !module.locked) { "Module is locked" }
            requireValidKey(this@ConnectedSetRegistry, key, value, key in base.valuesOf(module)) { "Key is invalid" }
            requireValidKey(this@ConnectedSetRegistry, key, value, key !in members) { "Key is already registered" }
            members += key
        }
        fun add(key: E) = set(key, Unit)

        override fun validate() {}
        operator fun contains(key: E): Boolean = key in members
        override fun getValueOrNull(key: E): Unit? = if (key in members) Unit else null
    }

    override fun get(module: ChessModule): Block = blocks.getOrPut(module) { Block(module) }

    override fun getKeyModule(key: E): ChessModule = base[key].module

    val elements: Set<E> get() = simpleKeys
    override val simpleKeys: Set<E>
        get() = blocks.flatMap { b -> b.value.keys }.toSet()
    override val keys: Set<RegistryKey<E>>
        get() = blocks.flatMap { b -> b.value.keys.map { RegistryKey(b.key, it) } }.toSet()

    override val values: Collection<Unit> get() = simpleKeys.map { }
}

class ConstantRegistry<E>(name: String) : Registry<Unit, E, ConstantRegistry<E>.Block>(name) {
    private val blocks = mutableMapOf<ChessModule, Block>()

    inner class Block internal constructor(module: ChessModule) : RegistryBlock<Unit, E>(module) {
        private var member: E? = null
        override val keys: Set<Unit> get() = setOfNotNull(member?.let {})
        override val values: Collection<E> = listOfNotNull(member)

        override fun set(key: Unit, value: E) {
            requireValidKey(this@ConstantRegistry, key, value, !module.locked) { "Module is locked" }
            requireValidKey(this@ConstantRegistry, key, value, member == null) { "Key is already registered" }
            member = value
        }
        fun set(value: E) = set(Unit, value)

        override fun validate() = requireValid(this@ConstantRegistry, member != null) { "Registry incomplete" }
        override fun getValueOrNull(key: Unit): E? = member

        fun getOrNull() = getOrNull(Unit)
        fun get() = get(Unit)
    }

    override fun get(module: ChessModule): Block = blocks.getOrPut(module) { Block(module) }

    override val keys: Set<RegistryKey<Unit>>
        get() = blocks.flatMap { b -> b.value.keys.map { RegistryKey(b.key, it) } }.toSet()

    override val values: Collection<E> get() = blocks.values.flatMap { it.values }
}