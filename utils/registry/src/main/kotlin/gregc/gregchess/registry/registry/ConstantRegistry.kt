package gregc.gregchess.registry.registry

import gregc.gregchess.registry.ChessModule
import gregc.gregchess.registry.RegistryKey

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

    operator fun set(module: ChessModule, value: E) = get(module).set(value)
}