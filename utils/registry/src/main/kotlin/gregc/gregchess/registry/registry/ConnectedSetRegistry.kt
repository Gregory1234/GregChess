package gregc.gregchess.registry.registry

import gregc.gregchess.registry.ChessModule
import gregc.gregchess.registry.RegistryKey
import gregc.gregchess.registry.view.FiniteBiRegistryView
import gregc.gregchess.registry.view.FiniteSplitRegistryView

class ConnectedSetRegistry<E>(name: String, val base: FiniteBiRegistryView<*, E>) : Registry<E, Unit, ConnectedSetRegistry<E>.Block>(name),
    FiniteSplitRegistryView<E, Unit> {
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

    fun add(key: E) = set(getKeyModule(key), key, Unit)

    operator fun plusAssign(key: E) = add(key)

    operator fun contains(key: E) = get(getKeyModule(key)).contains(key)
}