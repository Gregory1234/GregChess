package gregc.gregchess.registry.registry

import gregc.gregchess.registry.ChessModule
import gregc.gregchess.registry.RegistryKey
import gregc.gregchess.registry.view.*

abstract class RegistryBlock<K, T>(val module: ChessModule) : FiniteRegistryBlockView<K, T> {
    abstract fun set(key: K, value: T)
    abstract fun validate()
}

abstract class Registry<K, T, B : RegistryBlock<K, T>>(val name: String) : FiniteRegistryView<K, T> {
    init {
        @Suppress("LeakingThis")
        REGISTRIES += this
    }

    abstract operator fun get(module: ChessModule): B

    final override fun get(module: ChessModule, key: K): T = get(module)[key]
    final override fun getOrNull(module: ChessModule, key: K): T? = get(module).getOrNull(key)
    final override fun get(key: RegistryKey<K>): T = get(key.module, key.key)
    final override fun getOrNull(key: RegistryKey<K>): T? = getOrNull(key.module, key.key)
    final override fun keysOf(module: ChessModule): Set<K> = get(module).keys
    override fun valuesOf(module: ChessModule): Collection<T> = get(module).values

    operator fun set(key: RegistryKey<K>, value: T) = set(key.module, key.key, value)
    operator fun set(module: ChessModule, key: K, value: T) = get(module).set(key, value)

    companion object {
        @JvmField
        val REGISTRIES = mutableListOf<Registry<*,*,*>>()
    }
}

abstract class BiRegistryBlock<K, T>(module: ChessModule) : RegistryBlock<K, T>(module), FiniteBiRegistryBlockView<K, T>

abstract class BiRegistry<K, T, B : BiRegistryBlock<K, T>>(name: String) : Registry<K, T, B>(name), FiniteBiRegistryView<K, T> {
    final override fun valuesOf(module: ChessModule): Set<T> = get(module).values
}