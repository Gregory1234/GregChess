package gregc.gregchess.registry.view

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