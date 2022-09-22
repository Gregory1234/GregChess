package gregc.gregchess.registry.registry

import gregc.gregchess.registry.ChessModule
import gregc.gregchess.registry.RegistryKey
import gregc.gregchess.registry.view.FiniteBiRegistryView
import gregc.gregchess.registry.view.FiniteSplitRegistryView

class ConnectedBiRegistry<K, T>(name: String, val base: FiniteBiRegistryView<*, K>) : BiRegistry<K, T, ConnectedBiRegistry<K, T>.Block>(name),
    FiniteSplitRegistryView<K, T> {
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

    operator fun set(key: K, value: T) = set(getKeyModule(key), key, value)
}