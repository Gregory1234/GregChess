package gregc.gregchess.registry

import gregc.gregchess.ChessModule

interface RegistryView<K, T> {
    operator fun get(key: RegistryKey<K>): T = get(key.module, key.key)
    fun getOrNull(key: RegistryKey<K>): T? = getOrNull(key.module, key.key)
    operator fun get(module: ChessModule, key: K): T = getOrNull(module, key)!!
    fun getOrNull(module: ChessModule, key: K): T?
}

interface FiniteRegistryView<K, T> : RegistryView<K, T> {
    val keys: Set<RegistryKey<K>>
    val values: Collection<T>
    fun keysOf(module: ChessModule): Set<K>
    fun valuesOf(module: ChessModule): Collection<T>
}

interface BiRegistryView<K, T> : RegistryView<K, T> {
    operator fun get(value: T): RegistryKey<K> = getOrNull(value)!!
    fun getOrNull(value: T): RegistryKey<K>?
    fun getKey(value: T): K = getKeyOrNull(value)!!
    fun getKeyOrNull(value: T): K? = getOrNull(value)?.key
    fun getModule(value: T): ChessModule = getModuleOrNull(value)!!
    fun getModuleOrNull(value: T): ChessModule? = getOrNull(value)?.module
}

interface FiniteBiRegistryView<K, T> : BiRegistryView<K, T>, FiniteRegistryView<K, T> {
    override val values: Set<T>
    override fun valuesOf(module: ChessModule): Set<T>
}

interface RegistryBlockView<K, T> {
    fun getValueOrNull(key: K): T?
    fun getOrNull(key: K): T? = getValueOrNull(key)
    fun getValue(key: K): T = getValueOrNull(key)!!
    operator fun get(key: K): T = getOrNull(key)!!
}

interface FiniteRegistryBlockView<K, T> : RegistryBlockView<K, T> {
    val keys: Set<K>
    val values: Collection<T>
}

interface BiRegistryBlockView<K, T> : RegistryBlockView<K, T> {
    fun getKeyOrNull(value: T): K?
    fun getKey(value: T): K = getKeyOrNull(value)!!
}

interface FiniteBiRegistryBlockView<K, T> : BiRegistryBlockView<K, T>, FiniteRegistryBlockView<K, T> {
    override val values: Set<T>
}

@JvmSynthetic
fun <K, T> BiRegistryBlockView<K, T>.getOrNull(value: T): K? = getKeyOrNull(value)
@JvmSynthetic
fun <K, T> BiRegistryBlockView<K, T>.get(value: T): K = getOrNull(value)!!

class ChainRegistryView<K, M, T>(val base: RegistryView<K, M>, val extension: RegistryView<M, T>) : RegistryView<K, T> {
    override fun getOrNull(module: ChessModule, key: K): T? =
        base.getOrNull(module, key)?.let { extension.getOrNull(module, it) }
}