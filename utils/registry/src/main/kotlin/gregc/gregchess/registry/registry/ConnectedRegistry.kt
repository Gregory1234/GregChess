package gregc.gregchess.registry.registry

import gregc.gregchess.registry.ChessModule
import gregc.gregchess.registry.RegistryKey
import gregc.gregchess.registry.view.FiniteBiRegistryView
import gregc.gregchess.registry.view.FiniteSplitRegistryView

class ConnectedRegistry<K, T>(name: String, val base: FiniteBiRegistryView<*, K>) : Registry<K, T, ConnectedRegistry<K, T>.Block>(name),
    FiniteSplitRegistryView<K, T> {
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

    operator fun set(key: K, value: T) = set(getKeyModule(key), key, value)
}