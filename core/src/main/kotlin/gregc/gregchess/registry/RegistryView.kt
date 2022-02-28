package gregc.gregchess.registry

import gregc.gregchess.ChessModule

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

interface RegistryBlockView<K, T> {
    fun getValueOrNull(key: K): T?
    fun getOrNull(key: K): T? = getValueOrNull(key)
    fun getValue(key: K): T = getValueOrNull(key) ?: throw RegistryKeyNotFoundException(key)
    operator fun get(key: K): T = getOrNull(key) ?: throw RegistryKeyNotFoundException(key)
}

interface FiniteRegistryBlockView<K, T> : RegistryBlockView<K, T> {
    val keys: Set<K>
    val values: Collection<T>
}

interface BiRegistryBlockView<K, T> : RegistryBlockView<K, T> {
    fun getKeyOrNull(value: T): K?
    fun getKey(value: T): K = getKeyOrNull(value) ?: throw RegistryKeyNotFoundException(value)
}

interface FiniteBiRegistryBlockView<K, T> : BiRegistryBlockView<K, T>, FiniteRegistryBlockView<K, T> {
    override val values: Set<T>
}

class ChainRegistryView<K, M, T>(val base: RegistryView<K, M>, val extension: RegistryView<M, T>) : RegistryView<K, T> {
    override fun getOrNull(module: ChessModule, key: K): T? =
        base.getOrNull(module, key)?.let { extension.getOrNull(module, it) }
}