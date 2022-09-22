package gregc.gregchess.registry.registry

import gregc.gregchess.registry.ChessModule
import gregc.gregchess.registry.RegistryKey
import gregc.gregchess.registry.view.FiniteBiRegistryView
import gregc.gregchess.registry.view.FiniteSplitRegistryView

class PartialConnectedRegistry<K, T>(name: String, val base: FiniteBiRegistryView<*, K>) : Registry<K, T, PartialConnectedRegistry<K, T>.Block>(name),
    FiniteSplitRegistryView<K, T> {
    private val blocks = mutableMapOf<ChessModule, Block>()

    inner class Block internal constructor(module: ChessModule) : RegistryBlock<K, T>(module) {
        private val members = mutableMapOf<K, T>()

        override val keys: Set<K> = members.keys
        override val values: Collection<T> = members.values

        override fun set(key: K, value: T) {
            requireValidKey(this@PartialConnectedRegistry, key, value, !module.locked) { "Module is locked" }
            requireValidKey(this@PartialConnectedRegistry, key, value, key in base.valuesOf(module)) { "Key is invalid" }
            requireValidKey(this@PartialConnectedRegistry, key, value, key !in members) { "Key is already registered" }
            members[key] = value
        }

        override fun getValueOrNull(key: K): T? = members[key]

        override fun validate() {}

    }

    override fun get(module: ChessModule): Block = blocks.getOrPut(module) { Block(module) }

    override fun getKeyModule(key: K): ChessModule = base[key].module

    override val simpleKeys: Set<K>
        get() = blocks.flatMap { b -> b.value.keys }.toSet()
    override val keys: Set<RegistryKey<K>>
        get() = blocks.flatMap { b -> b.value.keys.map { RegistryKey(b.key, it) } }.toSet()
    override val values: Collection<T>
        get() = blocks.flatMap { b -> b.value.values }

    operator fun set(key: K, value: T) = set(getKeyModule(key), key, value)
}