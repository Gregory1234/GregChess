package gregc.gregchess.registry.view

import gregc.gregchess.registry.ChessModule
import gregc.gregchess.registry.RegistryKey

class RegistryKeyNotFoundException(key: Any?) : IllegalArgumentException(key.toString())

interface RegistryView<K, T> {
    operator fun get(key: RegistryKey<K>): T = get(key.module, key.key)
    fun getOrNull(key: RegistryKey<K>): T? = getOrNull(key.module, key.key)
    operator fun get(module: ChessModule, key: K): T = getOrNull(module, key) ?: throw RegistryKeyNotFoundException(RegistryKey(module, key))
    fun getOrNull(module: ChessModule, key: K): T?
}

interface FiniteRegistryView<K, T> : RegistryView<K, T> {
    val keys: Set<RegistryKey<K>>
    val values: Collection<T>
    fun keysOf(module: ChessModule): Set<K>
    fun valuesOf(module: ChessModule): Collection<T>
}

interface SplitRegistryView<K, T> : RegistryView<K, T> {
    fun getOrNull(key: K): T? = getKeyModule(key)?.let { m -> getOrNull(m, key) }
    operator fun get(key: K): T = getOrNull(key) ?: throw RegistryKeyNotFoundException(key)
    fun getKeyModule(key: K): ChessModule?
    fun completeKey(key: K): RegistryKey<K>? = getKeyModule(key)?.let { m -> RegistryKey(m, key) }
}

interface FiniteSplitRegistryView<K, T> : FiniteRegistryView<K, T>, SplitRegistryView<K, T> {
    override val keys: Set<RegistryKey<K>> get() = simpleKeys.mapNotNull(::completeKey).toSet()
    val simpleKeys: Set<K>
}

interface BiRegistryView<K, T> : RegistryView<K, T> {
    operator fun get(value: T): RegistryKey<K> = getOrNull(value) ?: throw RegistryKeyNotFoundException(value)
    fun getOrNull(value: T): RegistryKey<K>?
}

interface FiniteBiRegistryView<K, T> : FiniteRegistryView<K, T>, BiRegistryView<K, T> {
    override val values: Set<T>
    override fun valuesOf(module: ChessModule): Set<T>
}