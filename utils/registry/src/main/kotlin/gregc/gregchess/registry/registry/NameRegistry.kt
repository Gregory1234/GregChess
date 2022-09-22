package gregc.gregchess.registry.registry

import gregc.gregchess.registry.ChessModule
import gregc.gregchess.registry.RegistryKey

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